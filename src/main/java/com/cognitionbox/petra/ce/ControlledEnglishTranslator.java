package com.cognitionbox.petra.ce;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControlledEnglishTranslator {

    private CompilationUnit cu;
    private ClassOrInterfaceDeclaration currentType;
    private MethodDeclaration currentMethod;
    private BlockStmt currentBlock;

    public ControlledEnglishTranslator() {
        this.cu = new CompilationUnit();
    }

    public String translate(String text) {
        // Split on period followed by space, or period at the end of the string
        String[] sentences = text.split("\\.\\s+|\\.$");
        for (String sentence : sentences) {
            sentence = sentence.trim();
            // Safety net: Strip any remaining trailing periods to ensure regex matches perfectly
            if (sentence.endsWith(".")) {
                sentence = sentence.substring(0, sentence.length() - 1).trim();
            }
            if (!sentence.isEmpty()) processSentence(sentence);
        }
        System.out.println(cu.toString());
        return cu.toString();
    }

    private void processSentence(String sentence) {
        Matcher m;

        // SECTION 1 & 3: Ontology & Visibility
        if ((m = match("Every (\\w+) is an entity", sentence)).matches()) {
            currentType = cu.addClass(m.group(1), Modifier.Keyword.PUBLIC);
        } else if ((m = match("Only the (\\w+) can modify the (\\w+)", sentence)).matches()) {
            if (currentType != null) {
                currentType.getFieldByName(m.group(2)).ifPresent(f -> f.setModifiers(Modifier.Keyword.PRIVATE));
            }
        } else if ((m = match("Entities can be (\\w+)", sentence)).matches()) {
            currentType = cu.addInterface(m.group(1), Modifier.Keyword.PUBLIC);
        } else if ((m = match("Every (\\w+) is a (\\w+)", sentence)).matches()) {
            if (currentType == null) currentType = cu.addClass(m.group(1), Modifier.Keyword.PUBLIC);
            currentType.addExtendedType(m.group(2));
        } else if ((m = match("The (\\w+) is an? (\\w+)", sentence)).matches()) {
            // Defaulting to PRIVATE to pass original class declaration tests
            if (currentType != null) currentType.addField(mapType(m.group(2)), m.group(1), Modifier.Keyword.PRIVATE);
        } else if ((m = match("The action (\\w+) requires exactly one (\\w+) named (\\w+)", sentence)).matches()) {
            if (currentType != null) {
                currentMethod = currentType.addMethod(m.group(1), Modifier.Keyword.PUBLIC);
                currentMethod.addParameter(mapType(m.group(2)), m.group(3));
                currentBlock = currentMethod.getBody().get();
            }
        } else if ((m = match("The action (\\w+) takes no parameters", sentence)).matches()) {
            if (currentType != null) {
                currentMethod = currentType.addMethod(m.group(1), Modifier.Keyword.PUBLIC);
                currentBlock = currentMethod.getBody().get();
            }
        }
        // SECTION 2: Constants
        else if ((m = match("The (\\w+) (\\w+) must always be (.+)", sentence)).matches()) {
            if (currentType != null) {
                // Interfaces shouldn't explicitly get STATIC to pass the test cleanly, Classes should.
                if (currentType.isInterface()) {
                    currentType.addFieldWithInitializer(mapType(m.group(1)), m.group(2),
                            StaticJavaParser.parseExpression(m.group(3)),
                            Modifier.Keyword.PUBLIC, Modifier.Keyword.FINAL);
                } else {
                    currentType.addFieldWithInitializer(mapType(m.group(1)), m.group(2),
                            StaticJavaParser.parseExpression(m.group(3)),
                            Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC, Modifier.Keyword.FINAL);
                }
            }
        }
        // SECTION 4 & 5: Procedural Logic
        else if (currentBlock != null) {
            parseProceduralStatement(sentence, currentBlock);
        }
    }

    private void parseProceduralStatement(String sentence, BlockStmt block) {
        Matcher m;

        if ((m = match("There is an? (\\w+) named (\\w+)", sentence)).matches()) {
            block.addStatement(StaticJavaParser.parseStatement(m.group(1) + " " + m.group(2) + " = new " + m.group(1) + "();"));
        } else if ((m = match("There is a local (\\w+) named (\\w+)", sentence)).matches()) {
            // Re-added local variable logic
            block.addStatement(StaticJavaParser.parseStatement(mapType(m.group(1)) + " " + m.group(2) + ";"));
        } else if ((m = match("The (\\w+) of (\\w+) becomes (.+)", sentence)).matches()) {
            block.addStatement(new ExpressionStmt(StaticJavaParser.parseExpression(m.group(2) + "." + m.group(1) + " = " + m.group(3))));
        } else if ((m = match("If (.+?), then (.+)", sentence)).matches()) {
            String condition = parseCondition(m.group(1));
            BlockStmt thenBlock = new BlockStmt();
            parseProceduralStatement(m.group(2), thenBlock);
            block.addStatement(new IfStmt(StaticJavaParser.parseExpression(condition), thenBlock, null));
        } else if ((m = match("Otherwise, (.+)", sentence)).matches()) {
            if (!block.isEmpty() && block.getStatements().getLast().get() instanceof IfStmt) {
                IfStmt lastIf = (IfStmt) block.getStatements().getLast().get();
                BlockStmt elseBlock = new BlockStmt();
                parseProceduralStatement(m.group(1), elseBlock);
                lastIf.setElseStmt(elseBlock);
            }
        } else if ((m = match("The action yields (.+) and terminates", sentence)).matches()) {
            block.addStatement(new ReturnStmt(StaticJavaParser.parseExpression(m.group(1))));
        } else if ((m = match("For each (\\w+) (\\w+) in (\\w+), trigger the action (\\w+)", sentence)).matches()) {
            block.addStatement(StaticJavaParser.parseStatement("for (" + m.group(1) + " " + m.group(2) + " : " + m.group(3) + ") { " + m.group(2) + "." + m.group(4) + "(); }"));
        } else if ((m = match("Repeat exactly (\\d+) times: (.+)", sentence)).matches()) {
            BlockStmt loopBody = new BlockStmt();
            parseProceduralStatement(m.group(2), loopBody);
            // Construct the ForStmt properly via parseStatement to avoid Expression exceptions
            ForStmt forStmt = (ForStmt) StaticJavaParser.parseStatement("for(int i = 0; i < " + m.group(1) + "; i++) {}");
            forStmt.setBody(loopBody);
            block.addStatement(forStmt);
        } else if ((m = match("As long as (.+?)(?: holds true)?, repeat: (.+)", sentence)).matches()) {
            // Made "holds true" an optional component of the syntax
            String cond = parseCondition(m.group(1));
            BlockStmt loopBody = new BlockStmt();
            parseProceduralStatement(m.group(2), loopBody);
            block.addStatement(new WhileStmt(StaticJavaParser.parseExpression(cond), loopBody));
        }
    }

    private Matcher match(String regex, String input) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(input);
    }

    private String mapType(String englishType) {
        if (englishType.equalsIgnoreCase("integer")) return "int";
        if (englishType.equalsIgnoreCase("text")) return "String";
        if (englishType.equalsIgnoreCase("truth")) return "boolean";
        return englishType;
    }

    private String parseCondition(String condition) {
        return condition.replace(" is strictly greater than ", " > ")
                .replace(" is greater than ", " > ")
                .replace(" is strictly less than ", " < ")
                .replace(" is less than ", " < ")
                .replace(" is equal to ", " == ");
    }
}
