# Project Report Template Contract

## Reference

- Source: `C:\Users\Administrator\Downloads\Evening Program Project Report Template.docx`
- SHA-256: `22d9af5f8896dba5811fd62d6b90a68ba7c5e3d551eb05cca639839fd81533af`
- Rendered page count: 14
- Sections: 9
- Evidence: `template-reference.pdf`, `template-render/`, `template-style-evidence.json`, and `template-structure.txt` in this directory.

## Page System

- US Letter portrait, 8.5 x 11 inches.
- Cover section: left 1.5 inches, right 1.0 inch, top 2.0 inches, bottom 1.0 inch.
- Remaining sections: left 1.5 inches, right 1.0 inch, top and bottom 1.0 inch.
- New-page section breaks separate front matter and report body.
- Front matter uses lowercase Roman page numbers; report chapters use Arabic page numbers.
- Footer page numbers are centered. Headers are intentionally empty.

## Typography

- Body: Times New Roman, 12 pt, black, justified, 1.5 line spacing for report prose.
- Template source Normal style is Times New Roman 12 pt; the report increases line spacing to the academic-report convention without changing family or body size.
- Chapter titles: Times New Roman, 16 pt, bold, centered, uppercase, kept with following content.
- Level 2 headings: Times New Roman, 13 pt, bold, black, left aligned.
- Level 3 headings: Times New Roman, 12 pt, bold, black, left aligned.
- Figure and table captions: Times New Roman, 10 pt, centered, with the object kept on the same page where possible.
- Cover title: centered, bold, 18 pt, following the source cover hierarchy.

## Lists and Tables

- Lists use Word numbering definitions rather than typed bullet characters.
- Tables have explicit widths within the 6-inch usable body width, visible borders, repeated header rows, 0.08-inch cell margins, and no fixed row heights.
- Table header cells use light gray fill and bold centered text, consistent with the restrained academic source.

## Components

- Cover page with project title, degree statement, student placeholders, institution, and year.
- Signature page based on the source two-column metadata table.
- Acknowledgements, declaration, abstract, table of contents, list of tables, and list of figures.
- Numbered chapters beginning on new pages.
- Architecture, workflow, scoring, scalability, and case-management figures.
- Current UI screenshots used as implementation evidence.
- References and appendices.

## Content Flow

1. Cover and approval material.
2. Acknowledgements, declaration, and abstract.
3. Static table of contents, list of tables, and list of figures.
4. Introduction.
5. Background and related approaches.
6. Requirements and architecture.
7. Methodology.
8. Implementation.
9. Testing and results.
10. Problem-solution analysis.
11. Conclusion, limitations, and future work.
12. References and appendices.

## Slot Map

- All angle-bracket placeholders in the source are rewrite slots.
- Source instructional red text is removed.
- The signature metadata table is preserved as a component pattern and rewritten with project-specific labels and placeholders.
- Source list-of-table/list-of-figure examples are replaced with the generated report inventory.
- Empty chapter placeholders are expanded using cloned source-derived page and heading patterns.
- Student, roll, supervisor, designation, and submission metadata remain explicit placeholders because the user did not provide them.

## Package Preservation

- Preserve source theme, styles part, numbering foundations, page geometry, and section conventions as design authority.
- Rebuilding body XML is permitted because the source is an instructional template whose body consists of replaceable placeholders.
- Do not modify the retained reference file.

## Fidelity Gates

- Final document must remain US Letter with the source margins and Times New Roman hierarchy.
- Cover, signature page, front matter, chapter starts, centered footer numbering, and academic table/figure caption treatment must remain recognizable as template-derived.
- Render every final page and inspect for clipping, overlap, broken tables, blank accidental pages, stranded headings, unreadable screenshots, or inconsistent page numbers.
