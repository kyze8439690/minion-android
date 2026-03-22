package com.tomclaw.minion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by solkin on 01.08.17.
 */
class StringHelper {

    static String join(char delimiter, String[] tokens) {
        StringBuilder sb = new StringBuilder();
        boolean firstTime = true;
        for (Object token : tokens) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(delimiter);
            }
            sb.append(token);
        }
        return sb.toString();
    }

    static boolean startsWithChar(String line, char c) {
        if (line.length() == 0) return false;
        return line.charAt(0) == c;
    }

    static boolean endsWithChar(String line, char c) {
        if (line.length() == 0) return false;
        return line.charAt(line.length() - 1) == c;
    }

    static boolean containsChar(String line, char c) {
        return line.indexOf(c) != -1;
    }

    static List<String> splitByChar(String line, char c) {
        int off = 0;
        int next;
        ArrayList<String> list = new ArrayList<>();
        while ((next = line.indexOf(c, off)) != -1) {
            list.add(line.substring(off, next));
            off = next + 1;
        }
        // If no match was found, return this
        if (off == 0)
            return Collections.singletonList(line);

        // Add remaining segment
        list.add(line.substring(off));

        // Construct result
        int resultSize = list.size();
        while (resultSize > 0 && list.get(resultSize - 1).isEmpty()) {
            resultSize--;
        }
        if (resultSize == list.size()) {
            return list;
        } else {
            return list.subList(0, resultSize);
        }
    }

}
