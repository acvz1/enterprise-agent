# Note Writing

Read the current note completely before editing it. Preserve user-authored content, format, section style, and unrelated changes. Never reconstruct an existing note from conversation memory.

## Select the Note Type

Use a concept note for one unfamiliar syntax feature, type, annotation, library API, or local mechanism.

Use a module summary only after the learner understands the module's central call chain.

Use a chapter or milestone summary only after the main concepts have been questioned, applied, or restated.

## Concept Note

Use only the headings that add value; do not force every heading into every note.

```markdown
---

### 000. Topic

**需求**

The one project problem this concept solves.

**代码位置**

Exact file and line/block.

**核心逻辑**

The smallest faithful code and input/action/output flow.

**实操验证**

One executed change or request and its observed result, when available.

**结论**

One causal statement.

**易错点**

At most one high-value boundary, only when needed.
```

Do not claim an experiment was performed when it was only proposed.

## Editing Discipline

- Back up the note before a substantial structural rewrite.
- Make the smallest exact insertion around a known heading or index entry.
- Update the numbered section and quick index in the same edit.
- Accept either plain quick-index links or Markdown-list links; preserve the note's existing style.
- Preserve the learner's wording when it is correct.
- Put one blank line before and after a horizontal rule.
- Never use a broad global rewrite to normalize separators.
- Keep source links exact and clickable when supported.

## Validation

After editing, verify:

```text
index entry exists exactly once
numbered heading exists exactly once
index and heading numbers match
code fences are paired
blank lines surround horizontal rules
referenced local files and images exist
final section is complete
no unrelated content changed
```

Run:

```powershell
python scripts/validate_markdown_notes.py "path/to/note.md"
```

Treat structural validation failures as editing defects. A missing optional local image may remain a warning, but report it accurately.
