Fonts bundled with the report (so nothing is ever taken from the machine or fetched at print time)

Inter        inter-latin-*.woff2     the report's body font           licence: Inter-LICENSE.txt (SIL OFL 1.1)
Selawik      selawik-*.woff2         the brand block, the report title and the page footer
                                      (regular 400, semibold 600, bold 700)   licence: Selawik-LICENSE.txt (SIL OFL 1.1)

Why Selawik: the reference tool (docs/reference/nexlyn-bgv-report-v3.2.html) gives the brand block and the footer no font
file. It uses the system font stack "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif",
so each person saw their own computer's font: Segoe UI on Windows, and Arial (Liberation Sans) in the Linux server
container. Segoe UI itself may not be redistributed. Selawik is Microsoft's own open-licence font, designed to have the
same letter widths and a very similar look, so the layout matches what Windows users saw. Source: https://github.com/microsoft/Selawik
release 1.01 (files selawk.woff2, selawksb.woff2, selawkb.woff2). Weights 800 and 900 use the bold face (Selawik has no black weight).
