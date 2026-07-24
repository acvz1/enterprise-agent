# Note Writing

Read the current note completely before editing it. Preserve user-authored content, format, quick-index style, section numbering, and unrelated changes. Never reconstruct an existing note from conversation memory.

## Purpose

A study note must let the learner's future self reconstruct the code logic without reopening the entire conversation.

Use Occam's razor to remove material that does not support reconstruction. Do not remove the causal chain itself.

## Select the Note Type

Use a concept note for one unfamiliar syntax feature, type, annotation, library API, or local mechanism.

Use a module summary only after the learner understands the module's central call chain.

Use a chapter or milestone summary only after the main concepts have been questioned, applied, or restated.

Do not mark a chapter complete because the assistant reached the end of a file.

## Required Causal Spine

Every useful note must preserve:

~~~text
project requirement
  -> concrete problem
  -> exact code entry
  -> input
  -> important execution decisions
  -> output or state change
  -> why this solves the problem
  -> one important failure boundary, when needed
~~~

The note may be concise, but it must not contain only terminology and conclusions.

## Concept Note

Use only headings that add value:

~~~markdown
---

### 000. Topic

**需求**

The one project problem this concept solves.

**代码位置**

Exact clickable file and line/block.

**核心逻辑**

The smallest faithful code plus the complete input/action/output causal chain.

**实操验证**

One action actually performed and its observed result, when available.

**结论**

One causal statement in the learner's language.

**易错点**

At most one high-value boundary that changes the result.
~~~

Do not claim an experiment was performed when it was only proposed.

## Module or Chapter Note

Keep these elements in order:

~~~text
why the module exists
responsibility boundary
representative end-to-end call chain
core state or data structure
necessary code excerpts
verified behavior
one real limitation or TODO
compact conclusion
~~~

Include concrete code only where it carries a business decision or an unfamiliar mechanism. Skip imports, getters/setters, repetitive CRUD, and equivalent branches.

## Editing Discipline

- Back up the note before a substantial structural rewrite.
- Make the smallest exact insertion around a known heading or index entry.
- Update the numbered section and quick index in the same edit.
- Preserve the note's existing bullet or non-bullet index style.
- Preserve the learner's wording when materially correct.
- Put one blank line before and after a horizontal rule.
- Never use a broad global rewrite to normalize separators.
- Never recreate content the learner explicitly deleted or rejected.
- Never edit a nearby section merely because its topic seems related.
- At automatic chapter completion, append or update only that chapter.

## Future-Self Test

Before saving, answer:

~~~text
Can the learner identify the exact file and code entry?
Can they explain why this module or concept exists?
Can they follow input -> decisions -> output?
Can they distinguish verified behavior from a proposed improvement?
Can they recover the logic without the original chat?
~~~

If any answer is no, restore the missing causal link. Do not add unrelated breadth.

## Validation

After editing, verify:

~~~text
index entry exists exactly once
numbered heading exists exactly once
index and heading numbers match
code fences are paired
blank lines surround horizontal rules
referenced local files and images exist
final section is complete
no unrelated content changed
~~~

Run:

~~~powershell
python scripts/validate_markdown_notes.py "path/to/note.md"
~~~

Treat structural validation failures as editing defects. A missing optional local image may remain a warning, but report it accurately.
