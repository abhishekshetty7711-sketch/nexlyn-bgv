# PDF reports: speed, load and problems

## What the numbers are

Measured with `PdfLoadTest` on the development laptop (Windows 11, Chrome, 2 renders at a time, which is the
production default). The test case is a deliberately heavy one: 8 checks with two large scanned images each, giving
a 19-page, 10.5 MB PDF.

| | Result |
|---|---|
| One heavy report | about 10 seconds (median 10.5 s, slowest 11.4 s) |
| First report after start-up | about 13 seconds (the browser starts) |
| Throughput with 2 at a time | about 11 heavy reports a minute |
| Failures in 12 runs | none |

A normal case (3 to 5 checks with one image each) is well under 5 seconds. The numbers on the production machine
will differ (a small server can be slower than a laptop): run the test there after the first deploy if it matters.

## Running the test

On the development machine, with Chrome, Chromium or Edge installed (or `CHROMIUM_PATH` set):

```bash
cd backend
./mvnw -pl modules/reports -am test -Dtest=PdfLoadTest -Dtest.excluded.groups=none -Dgroups=load -Dsurefire.failIfNoSpecifiedTests=false
cat modules/reports/target/pdf-load.txt
```

Options: `-Dload.reports=30 -Dload.concurrency=4`. It is not run by the normal build.

## How the system protects itself

- At most `REPORT_MAX_CONCURRENT_RENDERS` (default 2) reports print at once; more requests wait in a queue and
  show as "Queued" then "Running" in the Generate section. Web requests are never slowed by printing.
- Each render starts its own short-lived browser and closes it. A rare "Printing failed" is retried once with a
  fresh browser.
- Uploaded images are re-encoded and limited to 25 megapixels and 10 MB, and embedded at most 2400 pixels wide.
- The backend container has a 2.5 GB memory limit (`mem_limit` in `infra/prod/docker-compose.yml`); one render
  uses a few hundred MB, mostly in the browser process.

## Problems

| Symptom | Likely cause | What to do |
|---|---|---|
| Every generate ends "Failed" | The browser cannot start | `docker compose --env-file .env logs backend`, look for `Chromium`. Check the container has memory (`docker stats`) and that `/tmp` is not full |
| Reports take minutes | Machine too small, or 3+ renders in parallel on 2 CPUs | Keep `REPORT_MAX_CONCURRENT_RENDERS=2` (or 1 on a 2 GB machine); use a bigger machine |
| The backend restarts under load (`OOMKilled` in `docker inspect`) | Too little memory for the limits | More RAM, or `REPORT_MAX_CONCURRENT_RENDERS=1` |
| A page is cut off or the page count is wrong | The layout check found overflow | The job fails with a message naming the page; shorten that check's text or split the images |
| Text shows as boxes for Indian scripts | Fonts missing in the image | The image includes Noto fonts; if you build your own image keep `fonts-noto-*` |
