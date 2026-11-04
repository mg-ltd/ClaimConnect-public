# Evidence-pack PDF layout

The pack is drawn with `android.graphics.pdf.PdfDocument` (no PDF
dependency) by `export/PdfPackGenerator.kt`. All content from the
previous layout is preserved — reference, revision, timestamps, every
manifest field, hashes, notices and captions — only the presentation
changed: aligned tables replace loose `Label: value` lines, and free
text sits in bordered boxes so nothing drifts around images.

## Page geometry

- A4 at 72 dpi (595 x 842 pt), 48 pt margins, 499 pt content width.
- Body content stays above y = 775; the footer (`reference · Page N`)
  sits at y = 809.
- Header `CLAIMCONNECT` on every page in theme teal `#006970`; section
  headings carry a teal rule underneath.

## Elements

- **Key-value tables** — incident header (reference, revision,
  generated), vehicle/driver, incident, and every manifest entry. Shaded
  label column, white value column, thin grid lines. Long unbroken
  tokens (SHA-256, evidence IDs) wrap mid-token inside the value cell.
- **Boxes** — shaded bordered paragraphs for the unverified-data
  notice, driver account, other parties, product limits, and the empty
  states. Tables and boxes flow across page breaks row by row.
- **Photo pages** — one page per photo: title, compact meta table
  (Evidence ID, SHA-256, added time, source), the display image centred
  (aspect preserved, at most 499 x 600 pt, with space reserved below),
  then the user caption in a shaded box.
- **AI appendix** — present only when the user kept AI notes during
  Review: one table per note (observation, linked photos, model
  version) under an "unverified" heading, before the photo pages.

## Layout math and tests

Pure geometry lives in framework-free `export/PdfLayout.kt` (wrapping,
photo rectangle, row heights) and is covered by JVM unit tests in
`export/PdfLayoutTest.kt`. The instrumented `RepositoryTest` ten-photo
pack test renders every page to catch drawing regressions. After layout
changes, re-verify on a physical device: generate a 10-photo pack, open
the preview, and confirm tables, images and captions stay aligned on
each page.
