package com.nexlyn.bgv.reports;

import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseReport.Check;
import com.nexlyn.bgv.cases.CaseReport.Detail;
import com.nexlyn.bgv.cases.CaseReport.Field;
import com.nexlyn.bgv.cases.CaseReport.FreeBlock;
import com.nexlyn.bgv.common.enums.CaseLifecycle;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.documents.DocumentApi;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Builds cases and documents for report tests, and a document store that lives in memory. */
public final class ReportFixtures {

    private ReportFixtures() {
    }

    public static Check check(String type, String group, String title, CheckStatus status) {
        return new Check(UUID.randomUUID(), type, group, title, "Summary of " + title, "Verifies " + title,
                "Document of " + title, status, "Standard", LocalDate.of(2026, 5, 19), LocalDate.of(2026, 6, 11),
                null, false, null, null,
                List.of(new Field("Full Name", "text", "Asha Rao", true), new Field("DOB", "date", "1994-05-17", false)),
                List.of(), List.of());
    }

    public static Check withRemarks(Check c, String remarks) {
        return new Check(c.id(), c.type(), c.iconGroup(), c.title(), c.summaryDescription(), c.cardVerifies(), c.documentName(),
                c.status(), c.verificationType(), c.requestedDate(), c.completedDate(), remarks, c.hasAttestation(),
                c.barCouncilNo(), c.disclaimer(), c.fields(), c.details(), c.freeBlocks());
    }

    public static Check withAttestation(Check c, String barCouncil, String disclaimer) {
        return new Check(c.id(), c.type(), c.iconGroup(), c.title(), c.summaryDescription(), c.cardVerifies(), c.documentName(),
                c.status(), c.verificationType(), c.requestedDate(), c.completedDate(), c.remarks(), true,
                barCouncil, disclaimer, c.fields(), c.details(), c.freeBlocks());
    }

    public static Check withFields(Check c, List<Field> fields, List<Detail> details, List<FreeBlock> blocks) {
        return new Check(c.id(), c.type(), c.iconGroup(), c.title(), c.summaryDescription(), c.cardVerifies(), c.documentName(),
                c.status(), c.verificationType(), c.requestedDate(), c.completedDate(), c.remarks(), c.hasAttestation(),
                c.barCouncilNo(), c.disclaimer(), fields, details, blocks);
    }

    /** A case with these checks, a photo id (or null), and default settings (4 cards, numeric dates, no watermark). */
    public static CaseReport report(List<Check> checks, UUID photoId) {
        return new CaseReport(UUID.randomUUID(), "NX-2026-0142", CaseLifecycle.DRAFT, LocalDate.of(2026, 6, 11),
                "Acme Corp\nPrivate Limited",
                new CaseReport.Candidate("Asha Rao", false, "Ravi Rao", "EMP-1001", LocalDate.of(1994, 5, 17), "+91 98765 43210", photoId),
                new CaseReport.Period(true, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 6, 11)),
                new CaseReport.Pill("COMPLETED", "Completed", "All Requested Verifications Completed"),
                new CaseReport.Overview(checks.size(), checks.size(), "Clear"),
                "All checks were <strong>completed</strong>.", "Approved for the next stage.",
                new CaseReport.Settings(4, "NUMERIC", false, "NEXLYN VERIFIED"), checks);
    }

    public static CaseReport withSettings(CaseReport r, int layout, String dateFormat, boolean watermark, String watermarkText) {
        return new CaseReport(r.caseId(), r.reportId(), r.lifecycle(), r.issueDate(), r.companyName(), r.candidate(), r.period(),
                r.pill(), r.overview(), r.analystRemarks(), r.finalRecommendation(),
                new CaseReport.Settings(layout, dateFormat, watermark, watermarkText), r.checks());
    }

    /** Distinct icon groups: the five shared ones, then one per check type. */
    public static List<Check> manyGroups(int count) {
        String[] groups = {"identity", "court", "address", "employment", "education", "REFERENCE", "POLICE", "UAN", "CREDIT",
                "DRUG_TEST", "DIRECTORSHIP", "GAP_REVIEW", "WORLD_CHECK", "OIG", "ADVERSE_MEDIA", "SOCIAL_MEDIA", "RESUME_REVIEW"};
        List<Check> checks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String group = i < groups.length ? groups[i] : "extra-" + i;
            checks.add(check(group.toUpperCase(), group, "Check " + (i + 1), CheckStatus.VERIFIED));
        }
        return checks;
    }

    // ---- pictures -------------------------------------------------------------------------------------

    public static byte[] png(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 4, height / 4);
        g.dispose();
        return write(image, "png");
    }

    public static byte[] jpeg(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return write(image, "jpg");
    }

    public static byte[] blankPdf(int pages) {
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] write(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- documents ---------------------------------------------------------------------------------------

    /** A document store in memory. */
    public static final class StubDocuments implements DocumentApi {
        private final Map<UUID, DocumentInfo> infos = new HashMap<>();
        private final Map<UUID, byte[]> bytes = new HashMap<>();
        private final Map<UUID, List<UUID>> byCheck = new HashMap<>();

        /** Adds a supporting document to a check; returns its id. */
        public UUID attach(UUID checkId, String mime, byte[] content, boolean moveToNextPage, boolean larger, Crop crop) {
            UUID id = UUID.randomUUID();
            List<UUID> list = byCheck.computeIfAbsent(checkId, k -> new ArrayList<>());
            String label = list.isEmpty() ? "Original Document" : "Additional Document " + list.size();
            infos.put(id, new DocumentInfo(id, UUID.randomUUID(), checkId, "CHECK_DOC", label, mime, null, null, moveToNextPage, larger, crop));
            bytes.put(id, content);
            list.add(id);
            return id;
        }

        public UUID photo(byte[] content) {
            UUID id = UUID.randomUUID();
            infos.put(id, new DocumentInfo(id, UUID.randomUUID(), null, "PHOTO", "Candidate photo", "image/png", null, null, false, false, null));
            bytes.put(id, content);
            return id;
        }

        public void lose(UUID documentId) {
            bytes.remove(documentId);
        }

        @Override
        public List<DocumentInfo> supportingDocuments(UUID checkId) {
            return byCheck.getOrDefault(checkId, List.of()).stream().map(infos::get).toList();
        }

        @Override
        public Optional<DocumentInfo> find(UUID documentId) {
            return Optional.ofNullable(infos.get(documentId));
        }

        @Override
        public byte[] content(UUID documentId) {
            byte[] found = bytes.get(documentId);
            if (found == null) {
                throw new IllegalStateException("missing");
            }
            return found;
        }
    }
}
