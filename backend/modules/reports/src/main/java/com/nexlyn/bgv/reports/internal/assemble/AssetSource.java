package com.nexlyn.bgv.reports.internal.assemble;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/** The brand pictures bundled with the application (logo, advocate seal), as data URIs, read once. */
@Component
public class AssetSource {

    private final String logo = load("static/report/images/logo.jpg", "image/jpeg");
    private final String seal = load("static/report/images/advocate-seal.png", "image/png");

    public String logoDataUri() {
        return logo;
    }

    public String advocateSealDataUri() {
        return seal;
    }

    private static String load(String path, String mime) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Missing bundled report asset " + path, e);
        }
    }
}
