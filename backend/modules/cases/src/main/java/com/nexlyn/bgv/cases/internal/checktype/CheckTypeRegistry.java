package com.nexlyn.bgv.cases.internal.checktype;

import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.DetailDefault;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.FieldDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.ItemField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads every {@code classpath:check-types/*.yml} once at startup (CLAUDE.md {@literal §8}). A definition
 * with a mistake stops the application from starting, with a message naming the file and the
 * field, so a typo can never reach an admin's screen.
 */
@Component
public class CheckTypeRegistry {

    private static final Logger log = LoggerFactory.getLogger(CheckTypeRegistry.class);
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,59}$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");
    private static final Set<String> ICON_GROUPS = Set.of("identity", "court", "address", "employment", "education", "unique");
    /** The candidate properties a field may be prefilled from. */
    public static final Set<String> PREFILL_PROPERTIES = Set.of(
            "fullName", "parentName", "dob", "phone", "street", "city", "state", "pin", "country", "employeeId");

    private final Map<String, CheckTypeDefinition> byCode;

    public CheckTypeRegistry() {
        this.byCode = load();
        log.info("Loaded {} check types", byCode.size());
    }

    /** Every type, in the order of the {@code order} key. */
    public List<CheckTypeDefinition> all() {
        return byCode.values().stream().sorted(Comparator.comparingInt(CheckTypeDefinition::order)).toList();
    }

    public Optional<CheckTypeDefinition> find(String code) {
        return Optional.ofNullable(code == null ? null : byCode.get(code));
    }

    // ---- loading ------------------------------------------------------------------------------

    private static Map<String, CheckTypeDefinition> load() {
        Map<String, CheckTypeDefinition> result = new LinkedHashMap<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:check-types/*.yml")) {
                String name = String.valueOf(resource.getFilename());
                try (InputStream in = resource.getInputStream()) {
                    Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
                    CheckTypeDefinition definition = parse(name, asMap(name, "the file", parsed));
                    if (result.put(definition.code(), definition) != null) {
                        throw fail(name, "the code " + definition.code() + " is used by another file too");
                    }
                    if (!name.equals(definition.code().toLowerCase() + ".yml")) {
                        throw fail(name, "the file must be named " + definition.code().toLowerCase() + ".yml");
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the check type definitions", e);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No check type definitions found in check-types/");
        }
        return Map.copyOf(result);
    }

    private static CheckTypeDefinition parse(String file, Map<String, Object> yaml) {
        String code = text(file, yaml, "code", true);
        if (!CODE.matcher(code).matches()) {
            throw fail(file, "code '" + code + "' must be capital letters, digits and underscores");
        }
        String group = text(file, yaml, "iconGroup", true);
        if (!ICON_GROUPS.contains(group)) {
            throw fail(file, "iconGroup '" + group + "' must be one of " + ICON_GROUPS);
        }
        List<FieldDefinition> fields = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Object item : list(file, yaml, "fields")) {
            FieldDefinition field = parseField(file, asMap(file, "a field", item));
            if (!keys.add(field.key())) {
                throw fail(file, "the field key '" + field.key() + "' appears twice");
            }
            fields.add(field);
        }
        if (fields.isEmpty()) {
            throw fail(file, "a check type needs at least one field");
        }
        List<DetailDefault> details = new ArrayList<>();
        for (Object item : list(file, yaml, "details")) {
            Map<String, Object> detail = asMap(file, "a detail", item);
            Object defaultValue = detail.get("default");
            details.add(new DetailDefault(text(file, detail, "label", true), defaultValue == null ? "" : String.valueOf(defaultValue)));
        }
        Object order = yaml.get("order");
        if (!(order instanceof Integer number)) {
            throw fail(file, "order must be a whole number");
        }
        return new CheckTypeDefinition(code, number, text(file, yaml, "displayName", true), text(file, yaml, "documentName", true),
                group, Boolean.TRUE.equals(yaml.get("attestationDefault")), text(file, yaml, "verificationTypeDefault", true),
                List.copyOf(fields), List.copyOf(details));
    }

    private static FieldDefinition parseField(String file, Map<String, Object> f) {
        String key = text(file, f, "key", true);
        if (!KEY.matcher(key).matches()) {
            throw fail(file, "field key '" + key + "' must be lower_snake_case");
        }
        FieldType type = fieldType(file, key, text(file, f, "type", true));
        String prefill = f.get("prefill") == null ? null : String.valueOf(f.get("prefill"));
        if (prefill != null) {
            String property = prefill.startsWith("candidate.") ? prefill.substring("candidate.".length()) : "";
            if (!PREFILL_PROPERTIES.contains(property)) {
                throw fail(file, "field '" + key + "': prefill '" + prefill + "' must be candidate.<one of " + PREFILL_PROPERTIES + ">");
            }
            prefill = property;
        }
        boolean sensitive = Boolean.TRUE.equals(f.get("sensitive"));
        if (sensitive && prefill != null) {
            throw fail(file, "field '" + key + "': a sensitive field cannot be prefilled from the candidate");
        }
        List<String> options = new ArrayList<>();
        for (Object option : f.get("options") instanceof List<?> l ? l : List.of()) {
            options.add(String.valueOf(option));
        }
        if (type == FieldType.SELECT && options.isEmpty()) {
            throw fail(file, "field '" + key + "': a select needs options");
        }
        List<ItemField> items = new ArrayList<>();
        for (Object item : f.get("itemFields") instanceof List<?> l ? l : List.of()) {
            Map<String, Object> i = asMap(file, "an item field", item);
            String itemKey = text(file, i, "key", true);
            FieldType itemType = fieldType(file, itemKey, text(file, i, "type", true));
            if (itemType == FieldType.REPEATABLE || itemType == FieldType.SELECT) {
                throw fail(file, "item field '" + itemKey + "' cannot be a " + itemType.json());
            }
            items.add(new ItemField(itemKey, text(file, i, "label", true), itemType));
        }
        if (type == FieldType.REPEATABLE && items.isEmpty()) {
            throw fail(file, "field '" + key + "': a repeatable needs itemFields");
        }
        return new FieldDefinition(key, text(file, f, "label", true), type, sensitive, Boolean.TRUE.equals(f.get("required")),
                prefill, Boolean.TRUE.equals(f.get("labelByParentType")), List.copyOf(options), List.copyOf(items));
    }

    private static FieldType fieldType(String file, String key, String value) {
        try {
            return FieldType.fromJson(value);
        } catch (IllegalArgumentException e) {
            throw fail(file, "field '" + key + "' has the unknown type '" + value + "'");
        }
    }

    private static String text(String file, Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            if (required) {
                throw fail(file, "'" + key + "' is missing");
            }
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static List<?> list(String file, Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw fail(file, "'" + key + "' must be a list");
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(String file, String what, Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw fail(file, what + " must be a mapping");
        }
        return (Map<String, Object>) value;
    }

    private static IllegalStateException fail(String file, String problem) {
        return new IllegalStateException("Invalid check type definition " + file + ": " + problem);
    }
}
