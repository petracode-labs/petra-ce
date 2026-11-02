# Petra Controlled English (petra-ce)

**petra-ce** is an open-source, neuro-symbolic requirements formalizer. It bridges the gap between natural language engineering standards and automated formal verification.

This tool functions as an **order-independent 2-pass compiler** that ingests technical requirement documents and interface specifications, compiling them into mathematically verifiable, parameterless Java 8 state machines. These artifacts are fully optimized for symbolic execution via the **Petra Verifier** (see [petra-examples](https://github.com/petracode-labs/petra-examples) repo).

---

## 🏛️ International Engineering Standards Alignment

`petra-ce` is strictly aligned with key ISO, IEEE, SAE, and defense standards to support safety-critical workflows out of the box:

| Architectural Layer | Standard Alignment | Implementation Details in `petra-ce` |
| :--- | :--- | :--- |
| **Requirements Syntax** | **ISO/IEC/IEEE 29148 & EARS** | Parses requirements matching EARS `WHILE` (State-driven) and `WHEN` (Event-driven) templates into pure, parameterless guarded state transitions. |
| **Component Composition** | **OMG SysML & SAE AADL** | Syntax like `The system Aircraft contains an EngineController named engine` explicitly generates Part Properties/nested subsystem hierarchies. |
| **Data Dictionary (ICD)** | **OMG IDL & AUTOSAR ARXML** | Structurally isolates system state vector injection (`has an integer named pressure`) from behavioral rules, matching DDS/ECU schemas. |
| **Vocabulary & Ontology** | **OMG SBVR (ISO/IEC 19786)** | Directly maps noun concepts and subclass relationships to strongly typed Object-Oriented AST abstractions. |
| **Target Formal Model** | **Event-B & DO-333** | Output is strictly constrained to bounded state spaces (no method parameters), facilitating deterministic $SAT$ evaluation in symbolic solvers. |

---

## 🛠️ The Two-Pass Compilation Model

In formal methods, system specifications are declarative combinations of logical assertions. To enforce order-independence across requirements documents, `petra-ce` utilizes a Two-Pass architecture via JavaParser:

### Pass 1: Structural & ICD Resolution
Scans the unstructured text stream to discover System classes, Field Interfaces (Data Dictionaries), and SysML Subcomponent Nesting.

```text
The system Aircraft has a truth named alarmActive.
The system Aircraft contains an EngineController named engine.
```

### Pass 2: Behavioral Binding (EARS State Rules)
Evaluates `WHILE` / `WHEN` triggers against the in-memory symbol table, allowing EARS rules to navigate nested paths safely.

```text
While engine.coreTemp is strictly greater than 950, the Aircraft shall set alarmActive to true.
```

### Output: Verifiable Java 8 State Machine
```java
public class Aircraft {
    private EngineController engine;
    private boolean alarmActive;

    public void onEngineCoreTempIsStrictlyGreaterThan950() {
        if (engine.coreTemp > 950) {
            this.alarmActive = true;
        }
    }
}
```

## 🚀 Usage with Petra

The resulting Java classes act as deterministic, finite state machines. They can be directly passed into the **Petra Verifier** (see [petra-examples](https://github.com/petracode-labs/petra-examples) repo) to prove formal properties via assertion reachability.
