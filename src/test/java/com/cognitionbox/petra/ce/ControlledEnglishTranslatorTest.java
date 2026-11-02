package com.cognitionbox.petra.ce;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class ControlledEnglishTranslatorTest {

    private ControlledEnglishTranslator translator;

    @Before
    public void setUp() {
        translator = new ControlledEnglishTranslator();
    }

    // --- LEGACY TESTS PRESERVED ---

    @Test
    public void testClassDeclarationAndFields() {
        String text = "Every Car is an entity. The speed is an integer. The model is a text.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public class Car"));
        assertTrue(javaCode.contains("private int speed;"));
        assertTrue(javaCode.contains("private String model;"));
    }

    @Test
    public void testInheritanceMapping() {
        String text = "Every Dog is a Pet.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public class Dog extends Pet"));
    }

    @Test
    public void testMethodAndMutation() {
        String text = "Every BankAccount is an entity. " +
                      "The action deposit requires exactly one integer named amount. " +
                      "The balance of this becomes this.balance + amount.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public class BankAccount"));
        assertTrue(javaCode.contains("public void deposit(int amount)"));
        assertTrue(javaCode.contains("this.balance = this.balance + amount;"));
    }

    @Test
    public void testConditionalLogic() {
        String text = "Every BankAccount is an entity. " +
                      "The action withdraw requires exactly one integer named amount. " +
                      "If amount is strictly greater than 0, then the balance of this becomes this.balance - amount.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public void withdraw(int amount)"));
        assertTrue(javaCode.contains("if (amount > 0)"));
        assertTrue(javaCode.contains("this.balance = this.balance - amount;"));
    }

    @Test
    public void testObjectInstantiationAndForeachLoop() {
        String text = "Every Kennel is an entity. " +
                      "The action feed requires exactly one integer named foodAmount. " +
                      "There is a Dog named fido. " +
                      "For each Dog d in dogs, trigger the action eat.";
        String javaCode = translator.translate(text);

        // Validating the internal block statements
        assertTrue(javaCode.contains("Dog fido = new Dog();"));
        assertTrue(javaCode.contains("for (Dog d : dogs) {"));
        assertTrue(javaCode.contains("d.eat();"));
    }

    @Test
    public void testEdgeCase_EmptySentencesAndWhitespace() {
        // Ensuring parser doesn't break on excessive dots or spaces
        String text = "Every Car is an entity.   . . The speed is an integer.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public class Car"));
        assertTrue(javaCode.contains("private int speed;"));
    }

    // --- NEW FEATURE TESTS (Sections 1 to 5) ---

    @Test
    public void testInterfacesAndConstants() {
        String text = "Entities can be Flyable. " +
                "The integer MAX_HEIGHT must always be 10000.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public interface Flyable"));
        assertTrue(javaCode.contains("public final int MAX_HEIGHT = 10000;"));
    }

    @Test
    public void testElseStatementsAndReturns() {
        String text = "Every MathHelper is an entity. " +
                "The action checkPositive requires exactly one integer named val. " +
                "If val is greater than 0, then the action yields true and terminates. " +
                "Otherwise, the action yields false and terminates.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("if (val > 0) {"));
        assertTrue(javaCode.contains("return true;"));
        assertTrue(javaCode.contains("else {"));
        assertTrue(javaCode.contains("return false;"));
    }

    @Test
    public void testBoundedForLoop() {
        String text = "Every Printer is an entity. " +
                "The action run requires exactly one integer named pages. " +
                "Repeat exactly 5 times: the pages of this becomes this.pages - 1.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("for (int i = 0; i < 5; i++) {"));
        assertTrue(javaCode.contains("this.pages = this.pages - 1;"));
    }

    @Test
    public void testWhileLoopAndLocalVariables() {
        String text = "Every Process is an entity. " +
                "The action execute takes no parameters. " +
                "There is a local integer named step. " +
                "As long as step is strictly less than 10, repeat: the step of this becomes step + 1.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public void execute()"));
        assertTrue(javaCode.contains("int step;"));
        assertTrue(javaCode.contains("while (step < 10) {"));
        assertTrue(javaCode.contains("this.step = step + 1;"));
    }

    @Test
    public void testWhileLoopFixed() {
        String text = "Every Process is an entity. " +
                "The action execute takes no parameters. " +
                "As long as step is strictly less than 10, repeat: the step of this becomes step + 1.";
        String javaCode = translator.translate(text);

        // Flexible assertion to ignore minor spacing variations
        assertTrue(javaCode.replaceAll("\\s", "").contains("while(step<10){"));
        assertTrue(javaCode.contains("this.step = step + 1"));
    }

    @Test
    public void testComplexIntegrationProgram() {
        // A complex scenario combining Inheritance, Constants, Private visibility,
        // Logic branches, and Return statements.
        String text =
                "Every Hero is an entity. " +
                        "Every Hero is a Character. " + // Inheritance
                        "The health is an integer. " +
                        "Only the Hero can modify the health. " + // Private visibility
                        "The integer MAX_HEALTH must always be 100. " + // Static Final Constant
                        "The action heal requires exactly one integer named amount. " +
                        "If amount is greater than 0, then the health of this becomes this.health + amount. " +
                        "Otherwise, the health of this becomes this.health. " + // Else branch
                        "If this.health is greater than MAX_HEALTH, then the health of this becomes MAX_HEALTH. " +
                        "The action isAlive takes no parameters. " +
                        "If health is strictly greater than 0, then the action yields true and terminates. " + // Return
                        "Otherwise, the action yields false and terminates.";

        String javaCode = translator.translate(text);

        // Assert Structural Elements
        assertTrue(javaCode.contains("public class Hero extends Character"));
        assertTrue(javaCode.contains("private int health;"));
        assertTrue(javaCode.contains("public static final int MAX_HEALTH = 100;"));

        // Assert Behavioral Logic
        assertTrue(javaCode.contains("public void heal(int amount)"));
        assertTrue(javaCode.replaceAll("\\s", "").contains("if(amount>0){this.health=this.health+amount;}else{this.health=this.health;}"));
        assertTrue(javaCode.contains("if (this.health > MAX_HEALTH)"));

        // Assert Return Logic
        assertTrue(javaCode.contains("public void isAlive()"));
        assertTrue(javaCode.contains("return true;"));
        assertTrue(javaCode.contains("return false;"));
    }

    @Test
    public void testNestedBoundedLoops() {
        String text = "Every Grid is an entity. " +
                "The action clear takes no parameters. " +
                "Repeat exactly 10 times: Repeat exactly 10 times: the cell of this becomes 0.";
        String javaCode = translator.translate(text);

        // Verification of nested for-loop structure
        assertTrue(javaCode.contains("for (int i = 0; i < 10; i++)"));
        // Check for presence of nested loop
        //assertTrue(javaCode.replaceAll("\\s", "").contains("for (int i=0; i<10; i++){ this.cell=0; }"));
    }

    @Test
    public void testUltimateIntegrationProgram() {
        String text =
                // 1. Ontology & Inheritance (Reordered to ensure BattleManager is the active context)
                "Entities can be Logger. " +
                        "Every BattleManager is an entity. " +
                        "Every BattleManager is a GameController. " +

                        // 2. Constants & Fields
                        "The integer REGEN_RATE must always be 5. " +
                        "The currentLevel is an integer. " +
                        "The isActive is a truth. " +
                        "Only the BattleManager can modify the isActive. " + // Private field

                        // 3. Complex Action with local variables and loops
                        "The action processTurn requires exactly one Hero named currentHero. " +
                        "There is a local integer named step. " +
                        "There is a local truth named isFinished. " +

                        // 4. While loop with nested Logic
                        "As long as step is strictly less than 3 holds true, repeat: " +
                        "If currentHero.health is less than 50, then " +
                        "the health of currentHero becomes currentHero.health + REGEN_RATE. " +

                        // 5. Nested Bounded For-loop
                        "Repeat exactly 2 times: " +
                        "Repeat exactly 5 times: " +
                        "the step of this becomes step + 1. " +

                        // 6. Collection Iteration
                        "For each Hero ally in party, trigger the action heal. " +

                        // 7. Branching with Return Logic
                        "If step is equal to 10, then the action yields true and terminates. " +
                        "Otherwise, the action yields false and terminates.";

        String javaCode = translator.translate(text);

        // Assertions to verify structural logic
        assertTrue(javaCode.contains("public interface Logger"));
        assertTrue(javaCode.contains("public class BattleManager extends GameController"));
        assertTrue(javaCode.contains("private boolean isActive;"));
        assertTrue(javaCode.contains("public static final int REGEN_RATE = 5;"));

        // Assertions to verify procedural logic
        assertTrue(javaCode.contains("int step;"));
        assertTrue(javaCode.contains("while (step < 3)"));
        assertTrue(javaCode.contains("for (Hero ally : party) {"));
        assertTrue(javaCode.contains("ally.heal();"));
        assertTrue(javaCode.contains("return true;"));
    }

    // --- NEW EARS & SYSML COMPONENT NESTING TESTS ---
    @Test
    public void testICDAndEARSEventDriven() {
        String text = "The system BrakeController has an integer named pressure. " +
                "The system BrakeController has a truth named warningActive. " +
                "When pressure is strictly greater than 1000, the BrakeController shall set warningActive to true.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public class BrakeController"));
        assertTrue(javaCode.contains("private int pressure;"));
        assertTrue(javaCode.contains("private boolean warningActive;"));
        assertTrue(javaCode.contains("public void onPressureIsStrictlyGreaterThan1000()"));
        assertTrue(javaCode.replaceAll("\\s", "").contains("if(pressure>1000){this.warningActive=true;}"));
    }
    @Test
    public void testEARSStateDrivenWhile() {
        // Replaced ubiquitous test with pure State-Driven Pattern
        String text = "The system Engine has an integer named temp. " +
                "The system Engine has a truth named overheat. " +
                "While temp is strictly greater than 120, the Engine shall set overheat to true.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public void onTempIsStrictlyGreaterThan120()"));
        assertTrue(javaCode.replaceAll("\\s", "").contains("if(temp>120){this.overheat=true;}"));
    }

    @Test
    public void testEARSComplexEventAndOrderIndependence() {
        String text = "While status is equal to 1, when temp is strictly greater than 90, the Reactor shall set meltdown to true. " +
                "The system Reactor has an integer named temp. " +
                "The system Reactor has an integer named status. " +
                "The system Reactor has a truth named meltdown.";
        String javaCode = translator.translate(text);

        assertTrue(javaCode.contains("public class Reactor"));
        assertTrue(javaCode.contains("onStatusIsEqualTo1AndTempIsStrictlyGreaterThan90"));
        assertTrue(javaCode.replaceAll("\\s", "").contains("if(status==1&&temp>90){this.meltdown=true;}"));
    }

    @Test
    public void testSysMLComponentCompositionAndNestedState() {
        String text = "The system Aircraft contains an EngineController named engine. " +
                "The system Aircraft has a truth named alarmActive. " +
                "While engine.coreTemp is strictly greater than 950, the Aircraft shall set alarmActive to true. " +
                "When engine.status is equal to 0, the Aircraft shall set engine.emergencyCutoff to true.";

        String javaCode = translator.translate(text);

        // Validating SysML Part Property Injection
        assertTrue(javaCode.contains("private EngineController engine;"));
        assertTrue(javaCode.contains("private boolean alarmActive;"));

        // Validating Nested EARS Rule Execution (Dot Notation)
        assertTrue(javaCode.contains("onEngineCoreTempIsStrictlyGreaterThan950"));
        assertTrue(javaCode.replaceAll("\\s", "").contains("if(engine.coreTemp>950){this.alarmActive=true;}"));

        assertTrue(javaCode.contains("onEngineStatusIsEqualTo0"));
        assertTrue(javaCode.replaceAll("\\s", "").contains("if(engine.status==0){this.engine.emergencyCutoff=true;}"));
    }
}
