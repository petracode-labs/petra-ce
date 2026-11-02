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

        // PASS 1: Ontology, Interfaces, ICD declarations, and Component Composition (Order Independent)
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.endsWith(".")) {
                sentence = sentence.substring(0, sentence.length() - 1).trim();
            }
            if (!sentence.isEmpty()) processStructuralSentence(sentence);
        }

        // Reset state for PASS 2
        currentType = null;
        currentMethod = null;
        currentBlock = null;

        // PASS 2: Behavioral logic, Legacy Implicit binding, and EARS rules
        for (String sentence : sentences) {
            sentence = sentence.trim();
            // Safety net: Strip any remaining trailing periods to ensure regex matches perfectly
            if (sentence.endsWith(".")) {
                sentence = sentence.substring(0, sentence.length() - 1).trim();
            }
            if (!sentence.isEmpty()) processBehavioralSentence(sentence);
        }
        return cu.toString();
    }

    private ClassOrInterfaceDeclaration getOrCreateClass(String name) {
        return cu.getClassByName(name).orElseGet(() -> cu.addClass(name, Modifier.Keyword.PUBLIC));
    }

    private ClassOrInterfaceDeclaration getOrCreateInterface(String name) {
        return cu.getInterfaceByName(name).orElseGet(() -> cu.addInterface(name, Modifier.Keyword.PUBLIC));
    }

    private void processStructuralSentence(String sentence) {
        Matcher m;

        // SECTION 1 & 3: Ontology & Visibility
        if ((m = match("Every (\\w+) is an entity", sentence)).matches()) {
            getOrCreateClass(m.group(1));
        } else if ((m = match("Entities can be (\\w+)", sentence)).matches()) {
            getOrCreateInterface(m.group(1));
        } else if ((m = match("Every (\\w+) is a (\\w+)", sentence)).matches()) {
            ClassOrInterfaceDeclaration clazz = getOrCreateClass(m.group(1));
            Matcher finalM = m;
            if (clazz.getExtendedTypes().stream().noneMatch(t -> t.getNameAsString().equals(finalM.group(2)))) {
                clazz.addExtendedType(m.group(2));
            }
        }
        // ICD Data Dictionary
        else if ((m = match("The system (\\w+) has an? (\\w+) named (\\w+)", sentence)).matches()) {
            ClassOrInterfaceDeclaration clazz = getOrCreateClass(m.group(1));
            if (!clazz.getFieldByName(m.group(3)).isPresent()) {
                clazz.addField(mapType(m.group(2)), m.group(3), Modifier.Keyword.PRIVATE);
            }
        }
        // NEW: SysML / AADL Component Composition (Part Properties)
        else if ((m = match("The system (\\w+) contains an? (\\w+) named (\\w+)", sentence)).matches()) {
            ClassOrInterfaceDeclaration clazz = getOrCreateClass(m.group(1));
            if (!clazz.getFieldByName(m.group(3)).isPresent()) {
                clazz.addField(m.group(2), m.group(3), Modifier.Keyword.PRIVATE);
            }
        }
    }

    private void processBehavioralSentence(String sentence) {
        Matcher m;

        // Sync legacy state context based on structural matches
        if ((m = match("Every (\\w+) is an entity", sentence)).matches() ||
                (m = match("Every (\\w+) is a (\\w+)", sentence)).matches() ||
                (m = match("The system (\\w+) has an? (\\w+) named (\\w+)", sentence)).matches() ||
                (m = match("The system (\\w+) contains an? (\\w+) named (\\w+)", sentence)).matches()) {
            currentType = getOrCreateClass(m.group(1));
            return;
        } else if ((m = match("Entities can be (\\w+)", sentence)).matches()) {
            currentType = getOrCreateInterface(m.group(1));
            return;
        }

        // --- EARS FEATURES (Pure Parameterless State Machine) ---
        // Note: Field matches changed from (\w+) to ([a-zA-Z0-9_.]+) to support nested paths (e.g. engine.temp)

        // EARS: Complex Event (WHILE + WHEN)
        else if ((m = match("While (.+?), when (.+?), (?:then )?the (\\w+) shall set ([a-zA-Z0-9_.]+) to (.+)", sentence)).matches()) {
            String precondition = parseCondition(m.group(1));
            String trigger = parseCondition(m.group(2));
            ClassOrInterfaceDeclaration clazz = getOrCreateClass(m.group(3));
            String fieldName = m.group(4);
            String value = m.group(5);

            String methodName = generateMethodName(m.group(1) + " and " + m.group(2));
            MethodDeclaration eventMethod = clazz.addMethod(methodName, Modifier.Keyword.PUBLIC);
            BlockStmt body = eventMethod.getBody().get();

            BlockStmt thenBlock = new BlockStmt();
            thenBlock.addStatement(new ExpressionStmt(StaticJavaParser.parseExpression("this." + fieldName + " = " + value)));
            body.addStatement(new IfStmt(StaticJavaParser.parseExpression(precondition + " && " + trigger), thenBlock, null));
            currentType = clazz;
            currentMethod = eventMethod;
            currentBlock = body;
        }
        // EARS: State-Driven (WHILE) replacing ubiquitous invariant
        else if ((m = match("While (.+?), (?:then )?the (\\w+) shall set ([a-zA-Z0-9_.]+) to (.+)", sentence)).matches()) {
            String precondition = parseCondition(m.group(1));
            ClassOrInterfaceDeclaration clazz = getOrCreateClass(m.group(2));
            String fieldName = m.group(3);
            String value = m.group(4);

            String methodName = generateMethodName(m.group(1));
            MethodDeclaration eventMethod = clazz.addMethod(methodName, Modifier.Keyword.PUBLIC);
            BlockStmt body = eventMethod.getBody().get();

            BlockStmt thenBlock = new BlockStmt();
            thenBlock.addStatement(new ExpressionStmt(StaticJavaParser.parseExpression("this." + fieldName + " = " + value)));
            body.addStatement(new IfStmt(StaticJavaParser.parseExpression(precondition), thenBlock, null));
            currentType = clazz;
            currentMethod = eventMethod;
            currentBlock = body;
        }
        // EARS: Event-Driven & Hazard (WHEN / IF)
        else if ((m = match("(?:When|If) (.+?), (?:then )?the (\\w+) shall set ([a-zA-Z0-9_.]+) to (.+)", sentence)).matches()) {
            String trigger = parseCondition(m.group(1));
            ClassOrInterfaceDeclaration clazz = getOrCreateClass(m.group(2));
            String fieldName = m.group(3);
            String value = m.group(4);

            String methodName = generateMethodName(m.group(1));
            MethodDeclaration eventMethod = clazz.addMethod(methodName, Modifier.Keyword.PUBLIC);
            BlockStmt body = eventMethod.getBody().get();

            BlockStmt thenBlock = new BlockStmt();
            thenBlock.addStatement(new ExpressionStmt(StaticJavaParser.parseExpression("this." + fieldName + " = " + value)));
            body.addStatement(new IfStmt(StaticJavaParser.parseExpression(trigger), thenBlock, null));
            currentType = clazz;
            currentMethod = eventMethod;
            currentBlock = body;
        }
        // --- LEGACY PARSING BRANCHES ---
        else if ((m = match("Only the (\\w+) can modify the (\\w+)", sentence)).matches()) {
            if (currentType != null) {
                currentType.getFieldByName(m.group(2)).ifPresent(f -> f.setModifiers(Modifier.Keyword.PRIVATE));
            }
        } else if ((m = match("The (\\w+) is an? (\\w+)", sentence)).matches()) {
            if (currentType != null && !currentType.getFieldByName(m.group(1)).isPresent()) {
                currentType.addField(mapType(m.group(2)), m.group(1), Modifier.Keyword.PRIVATE);
            }
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
        } else if ((m = match("The ([a-zA-Z0-9_.]+) of (\\w+) becomes (.+)", sentence)).matches()) {
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

    private String generateMethodName(String condition) {
        String[] words = condition.split("[^a-zA-Z0-9]+");
        StringBuilder sb = new StringBuilder("on");
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return sb.toString();
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
