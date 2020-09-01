package com.tomclaw.minion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tomclaw.minion.storage.Readable;
import com.tomclaw.minion.storage.Writable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.tomclaw.minion.StreamHelper.safeClose;
import static com.tomclaw.minion.StringHelper.join;

/**
 * Created by solkin on 27.07.17.
 */
@SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
public class Minion {

    public static final String DEFAULT_GROUP_NAME = "";

    private static final String COMMENT_START_UNIX = "#";
    private static final String COMMENT_START_WINDOWS = ";";
    private static final String COMMENT_START_SLASH = " //";
    private static final String GROUP_START = "[";
    private static final String GROUP_END = "]";
    private static final String KEY_VALUE_DIVIDER = "=";
    private static final String ARRAY_VALUE_DELIMITER = ",";

    private static Executor executor = Executors.newSingleThreadExecutor();

    private final Readable readable;
    private final Writable writable;
    private final boolean async;

    private final Map<String, IniGroup> groups = new LinkedHashMap<>();

    private Minion(Readable readable, Writable writable, boolean async) {
        this.readable = readable;
        this.writable = writable;
        this.async = async;
    }

    @Nullable
    public IniRecord setValue(
            @NonNull String name,
            @NonNull String key,
            @NonNull String... value
    ) {
        IniGroup group = getOrCreateGroup(name);
        return group.getOrCreateRecord(key, value);
    }

    @Nullable
    public String getValue(
            @NonNull String name,
            @NonNull String key
    ) {
        return getValue(name, key, null);
    }

    @Nullable
    public String getValue(
            @NonNull String name,
            @NonNull String key,
            @Nullable String defValue
    ) {
        String value = defValue;
        IniGroup group = getOrCreateGroup(name);
        IniRecord record = group.getRecord(key);
        if (record != null && record.hasValue()) {
            value = record.getValue();
        }
        return value;
    }

    @Nullable
    public String[] getValues(
            @NonNull String name,
            @NonNull String key
    ) {
        return getValues(name, key, null);
    }

    @Nullable
    public String[] getValues(
            @NonNull String name,
            @NonNull String key,
            @Nullable String[] defValue
    ) {
        String[] value = defValue;
        IniGroup group = getOrCreateGroup(name);
        IniRecord record = group.getRecord(key);
        if (record != null) {
            value = record.getValues();
        }
        return value;
    }

    @NonNull
    public IniGroup getOrCreateGroup(@NonNull String name) {
        synchronized (groups) {
            IniGroup group = getGroup(name);
            if (group == null) {
                group = addGroup(name);
            }
            return group;
        }
    }

    @Nullable
    public IniGroup getGroup(@NonNull String name) {
        return groups.get(name);
    }

    @NonNull
    public Set<String> getGroupNames() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    @NonNull
    public Collection<IniGroup> getGroups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public int getGroupsCount() {
        return groups.size();
    }

    @NonNull
    private IniGroup addGroup(String name) {
        IniGroup group = new IniGroup(name);
        groups.put(group.getName(), group);
        return group;
    }

    @Nullable
    public IniGroup removeGroup(String name) {
        return groups.remove(name);
    }

    @Nullable
    public IniRecord removeRecord(String name, String key) {
        IniGroup group = getGroup(name);
        if (group != null) {
            return group.removeRecord(key);
        }
        return null;
    }

    public void clear() {
        groups.clear();
    }

    public void store() {
        store(new EmptyResultCallback());
    }

    public void store(@NonNull final ResultCallback callback) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                storeSync(callback);
            }
        };
        if (async) {
            executor.execute(runnable);
        } else {
            runnable.run();
        }
    }

    private void storeSync(@NonNull final ResultCallback callback) {
        try {
            final OutputStream outputStream = writable.write();
            compile(outputStream);
            callback.onReady(this);
        } catch (Exception ex) {
            callback.onFailure(ex);
        }
    }

    private void compile(OutputStream outputStream) throws IOException {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            boolean isEmpty = true;
            for (IniGroup group : groups.values()) {
                if (!isEmpty) {
                    writer.newLine();
                }
                writer.write(GROUP_START + group.getName() + GROUP_END);
                isEmpty = false;
                for (IniRecord record : group.getRecords()) {
                    String value = join(ARRAY_VALUE_DELIMITER, record.getValues());
                    writer.newLine();
                    writer.write(record.getKey() + KEY_VALUE_DIVIDER + value);
                }
            }
            writer.flush();
        } finally {
            safeClose(writer);
        }
    }

    private void load(@NonNull final ResultCallback callback) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                loadSync(callback);
            }
        };
        if (async) {
            executor.execute(runnable);
        } else {
            runnable.run();
        }
    }

    private void loadSync(@NonNull ResultCallback callback) {
        try {
            final InputStream inputStream = readable.read();
            parse(inputStream);
            callback.onReady(this);
        } catch (Exception ex) {
            callback.onFailure(ex);
        }
    }

    private void parse(@NonNull InputStream inputStream) throws IOException, UnsupportedFormatException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            IniGroup lastGroup = new IniGroup(DEFAULT_GROUP_NAME);
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(COMMENT_START_UNIX) || line.startsWith(COMMENT_START_WINDOWS)) {
                    continue;
                }

                if (line.startsWith(GROUP_START) && line.endsWith(GROUP_END)) {
                    String name = line.substring(1, line.length() - 1);
                    lastGroup = addGroup(name);
                    continue;
                }

                if (line.contains(KEY_VALUE_DIVIDER)) {
                    int index = line.indexOf(KEY_VALUE_DIVIDER);
                    if (index <= 0) {
                        throw new UnsupportedFormatException();
                    }
                    String key = line.substring(0, index).trim();
                    String value = line.substring(index + 1);

                    String[] arrayValue = value.split(ARRAY_VALUE_DELIMITER);
                    String last = arrayValue[arrayValue.length - 1];
                    index = last.indexOf(COMMENT_START_SLASH);
                    if (index != -1) {
                        arrayValue[arrayValue.length - 1] = last.substring(0, index).trim();
                    }
                    lastGroup.getOrCreateRecord(key, arrayValue);
                } else if (line.contains(ARRAY_VALUE_DELIMITER)) {
                    String[] arrayValue = line.split(ARRAY_VALUE_DELIMITER);
                    String last = arrayValue[arrayValue.length - 1];
                    int index = last.indexOf(COMMENT_START_SLASH);
                    if (index != -1) {
                        arrayValue[arrayValue.length - 1] = last.substring(0, index).trim();
                    }
                    lastGroup.getOrCreateRecord(line, arrayValue);
                }
            }
        } finally {
            safeClose(reader);
        }
    }

    public static Builder lets() {
        return new Builder();
    }

    public static class Builder {

        private Readable readable;
        private Writable writable;
        private boolean async;
        private ResultCallback callback;

        private Builder() {
        }

        public Builder load(@NonNull Readable readable) {
            this.readable = readable;
            return this;
        }

        public Builder store(@NonNull Writable writable) {
            this.writable = writable;
            return this;
        }

        public Builder and() {
            // Empty method just for better syntax.
            return this;
        }

        public Minion sync() {
            this.async = false;
            this.callback = new EmptyResultCallback();
            return build();
        }

        public Minion async(@NonNull ResultCallback callback) {
            this.async = true;
            this.callback = callback;
            return build();
        }

        public Minion buildSimple() {
            readable = null;
            writable = null;
            async = false;
            callback = new EmptyResultCallback();
            return build();
        }

        private Minion build() {
            Minion minion = new Minion(readable, writable, async);
            minion.load(callback);
            return minion;
        }

    }

}
