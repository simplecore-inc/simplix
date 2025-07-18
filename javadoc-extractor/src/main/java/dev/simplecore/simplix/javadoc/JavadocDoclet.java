package dev.simplecore.simplix.javadoc;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.github.javaparser.javadoc.description.JavadocDescription;
import com.github.javaparser.utils.SourceRoot;
import lombok.Getter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Class for extracting Javadoc information from source code using JavaParser.
 */
public class JavadocDoclet {

    @Getter
    private static final List<ClassDocInfo> classDocInfos = new ArrayList<>();

    /**
     * Parse Java source files and extract Javadoc information.
     *
     * @param sourceDir directory containing Java source files
     * @throws IOException if an I/O error occurs
     */
    public static void parse(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        SourceRoot sourceRoot = new SourceRoot(sourcePath);
        
        List<ParseResult<CompilationUnit>> parseResults = sourceRoot.tryToParse();
        
        for (ParseResult<CompilationUnit> parseResult : parseResults) {
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                
                // Extract package name
                String packageName = cu.getPackageDeclaration().isPresent() ? 
                        cu.getPackageDeclaration().get().getNameAsString() : "";
                
                // Process classes and interfaces
                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                    ClassDocInfo classDocInfo = extractClassInfo(classDecl, packageName);
                    classDocInfos.add(classDocInfo);
                });
            }
        }
    }

    /**
     * Extract class information from ClassOrInterfaceDeclaration.
     *
     * @param classDecl the ClassOrInterfaceDeclaration object
     * @param packageName the package name
     * @return ClassDocInfo containing extracted information
     */
    private static ClassDocInfo extractClassInfo(ClassOrInterfaceDeclaration classDecl, String packageName) {
        ClassDocInfo classDocInfo = new ClassDocInfo();
        classDocInfo.setName(classDecl.getNameAsString());
        classDocInfo.setQualifiedName(packageName + "." + classDecl.getNameAsString());
        classDocInfo.setPackageName(packageName);
        classDocInfo.setModifiers(classDecl.getModifiers().toString());
        classDocInfo.setIsInterface(classDecl.isInterface());
        classDocInfo.setIsEnum(false); // JavaParser handles enums differently
        classDocInfo.setIsAnnotation(classDecl.isAnnotationDeclaration());
        
        // Extract Javadoc comment
        Optional<JavadocComment> javadocOpt = classDecl.getJavadocComment();
        if (javadocOpt.isPresent()) {
            Javadoc javadoc = javadocOpt.get().parse();
            classDocInfo.setComment(javadoc.getDescription().toText());
            
            // Extract tags
            Map<String, List<String>> tags = extractJavadocTags(javadoc);
            classDocInfo.setTags(tags);
        }
        
        // Extract fields
        List<FieldDocInfo> fieldDocInfos = new ArrayList<>();
        classDecl.getFields().forEach(field -> {
            field.getVariables().forEach(variable -> {
                FieldDocInfo fieldDocInfo = new FieldDocInfo();
                fieldDocInfo.setName(variable.getNameAsString());
                fieldDocInfo.setType(field.getElementType().asString());
                fieldDocInfo.setModifiers(field.getModifiers().toString());
                
                // Extract field Javadoc
                Optional<JavadocComment> fieldJavadocOpt = field.getJavadocComment();
                if (fieldJavadocOpt.isPresent()) {
                    Javadoc fieldJavadoc = fieldJavadocOpt.get().parse();
                    fieldDocInfo.setComment(fieldJavadoc.getDescription().toText());
                    
                    // Extract field tags
                    Map<String, List<String>> fieldTags = extractJavadocTags(fieldJavadoc);
                    fieldDocInfo.setTags(fieldTags);
                }
                
                fieldDocInfos.add(fieldDocInfo);
            });
        });
        classDocInfo.setFields(fieldDocInfos);
        
        // Extract methods
        List<MethodDocInfo> methodDocInfos = new ArrayList<>();
        classDecl.getMethods().forEach(method -> {
            MethodDocInfo methodDocInfo = new MethodDocInfo();
            methodDocInfo.setName(method.getNameAsString());
            methodDocInfo.setReturnType(method.getType().asString());
            methodDocInfo.setModifiers(method.getModifiers().toString());
            
            // Extract method Javadoc
            Optional<JavadocComment> methodJavadocOpt = method.getJavadocComment();
            if (methodJavadocOpt.isPresent()) {
                Javadoc methodJavadoc = methodJavadocOpt.get().parse();
                methodDocInfo.setComment(methodJavadoc.getDescription().toText());
                
                // Extract method tags
                Map<String, List<String>> methodTags = extractJavadocTags(methodJavadoc);
                methodDocInfo.setTags(methodTags);
                
                // Extract return comment
                methodJavadoc.getBlockTags().stream()
                    .filter(tag -> tag.getType() == JavadocBlockTag.Type.RETURN)
                    .findFirst()
                    .ifPresent(tag -> methodDocInfo.setReturnComment(tag.getContent().toText()));
            }
            
            // Extract parameters
            List<ParamDocInfo> paramDocInfos = new ArrayList<>();
            for (Parameter parameter : method.getParameters()) {
                ParamDocInfo paramDocInfo = new ParamDocInfo();
                paramDocInfo.setName(parameter.getNameAsString());
                paramDocInfo.setType(parameter.getType().asString());
                
                // Find parameter comment from @param tags
                if (methodJavadocOpt.isPresent()) {
                    Javadoc methodJavadoc = methodJavadocOpt.get().parse();
                    methodJavadoc.getBlockTags().stream()
                        .filter(tag -> tag.getType() == JavadocBlockTag.Type.PARAM)
                        .filter(tag -> tag.getName().isPresent() && tag.getName().get().equals(parameter.getNameAsString()))
                        .findFirst()
                        .ifPresent(tag -> paramDocInfo.setComment(tag.getContent().toText()));
                }
                
                paramDocInfos.add(paramDocInfo);
            }
            methodDocInfo.setParameters(paramDocInfos);
            
            methodDocInfos.add(methodDocInfo);
        });
        classDocInfo.setMethods(methodDocInfos);
        
        return classDocInfo;
    }
    
    /**
     * Extract Javadoc tags from Javadoc.
     *
     * @param javadoc the Javadoc object
     * @return map of tag names to tag values
     */
    private static Map<String, List<String>> extractJavadocTags(Javadoc javadoc) {
        Map<String, List<String>> tags = new HashMap<>();
        
        javadoc.getBlockTags().forEach(tag -> {
            String tagName = "@" + tag.getType().name().toLowerCase();
            if (!tags.containsKey(tagName)) {
                tags.put(tagName, new ArrayList<>());
            }
            tags.get(tagName).add(tag.getContent().toText());
        });
        
        return tags;
    }
}