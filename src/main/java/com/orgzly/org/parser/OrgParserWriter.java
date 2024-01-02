package com.orgzly.org.parser;

import com.orgzly.org.OrgHead;
import com.orgzly.org.OrgProperties;
import com.orgzly.org.OrgProperty;
import com.orgzly.org.OrgStringUtils;

import java.util.List;

public class OrgParserWriter {
    /** org-log-note-headings */
    private static final String[] ORG_LOG_NOTE_HEADINGS = new String[] {
            "CLOSING NOTE ",
            "State ",
            "Note taken on ",
            "Rescheduled from ",
            "Not scheduled, was ",
            "New deadline from ",
            "Removed deadline, was ",
            "Refiled on "
    };

    private OrgParserSettings settings;

    public OrgParserWriter() {
        this.settings = OrgParserSettings.getBasic();
    }

    public OrgParserWriter(OrgParserSettings settings) {
        this.settings = settings;
    }

    /**
     * Make sure content ends with at least 2 new-line characters, unless it's empty.
     * Required so notes can be just appended to it.
     *
     * @param content Preface
     * @return preface ready for appending
     */
    public String whiteSpacedFilePreface(String content) {
        if (content == null || content.length() == 0) {
            return "";
        } else {
            return OrgStringUtils.trimLines(content) + "\n\n";
        }
    }

    public String whiteSpacedHead(OrgNode node, boolean isIndented) {
        return whiteSpacedHead(node.getHead(), node.getLevel(), isIndented);
    }

    /**
     * Prepare heading for appending.
     * No white space is appended or prepended.
     *
     * @param head heading
     * @param level level (depth)
     * @param isIndented indented or not
     * @return heading ready for appending
     */
    public String whiteSpacedHead(OrgHead head, int level, boolean isIndented) {
        StringBuilder s = new StringBuilder();

        /* Append stars. */
        for (int i = 0; i < level; i++) {
            s.append("*");
        }

        s.append(" ");

        /* State keyword. */
        if (head.getState() != null) {
            s.append(head.getState()).append(" ");
        }

        /* Priority. */
        if (head.getPriority() != null) {
            s.append("[#").append(head.getPriority()).append("] ");
        }

        /* Title. */
        s.append(head.getTitle());

        /* Tags. */
        if (head.hasTags()) {

            /* Always add at least one space after the heading. */
            s.append(" ");

            String ts = tagsString(head.getTags());

            /* Figure out how many spaces we need to align the tags properly. */
            int padding = Math.abs(settings.tagsColumn) - OrgStringUtils.stringWidth(s.toString());

            /* Shift the tags left for users of org-indent-mode.
             *
             * The first level of indentation has already been added (the
             * heading asterisks), the indentation we add per level is 1 LESS
             * than settings.orgIndentIndentationPerLevel.
             */
            if (settings.orgIndentMode && settings.orgIndentIndentationPerLevel > 0) {
                padding -= (settings.orgIndentIndentationPerLevel - 1) * (level - 1);
            }

            if (settings.tagsColumn < 0) {
                padding -= OrgStringUtils.stringWidth(ts);
            }

            for (; padding > 0; padding--) {
                s.append(" ");
            }

            s.append(ts);
        }

        /* Anything that should go right under header, with no new-line in between. */
        boolean hasUnderHead = false;

        if (head.hasClosed()) {
            s.append("\n");
            appendIndent(s, level, isIndented);
            s.append("CLOSED: ").append(head.getClosed());

            hasUnderHead = true;
        }

        if (head.hasDeadline()) {
            if (hasUnderHead) {
                s.append(" ");
            } else {
                s.append("\n");
                appendIndent(s, level, isIndented);
            }

            s.append("DEADLINE: ").append(head.getDeadline());

            hasUnderHead = true;
        }

        if (head.hasScheduled()) {
            if (hasUnderHead) {
                s.append(" ");
            } else {
                s.append("\n");
                appendIndent(s, level, isIndented);
            }

            s.append("SCHEDULED: ").append(head.getScheduled());

            hasUnderHead = true;
        }

        if (head.hasClock()) {
            s.append("\n");
            appendIndent(s, level, isIndented);
            s.append("CLOCK: ").append(head.getClock());

            hasUnderHead = true;
        }

        /* We are at the end of title or properties here, with no new-line added. */

        if (head.hasProperties()) {
            s.append("\n");
            appendIndent(s, level, isIndented);
            s.append(":PROPERTIES:");

            OrgProperties properties = head.getProperties();
            for (OrgProperty property: properties.getAll()) {
                s.append("\n");
                appendIndent(s, level, isIndented);
                s.append(String.format(settings.propertyFormat, ":" + property.getName() + ":", property.getValue()));
            }

            s.append("\n");
            appendIndent(s, level, isIndented);
            s.append(":END:");

            hasUnderHead = true;
        }

        if (head.hasLogbook()) {
            s.append("\n");
            appendIndent(s, level, isIndented);
            s.append(":LOGBOOK:");

            for (String log: head.getLogbook()) {
                s.append("\n");
                appendIndent(s, level, isIndented);
                s.append(log);
            }

            s.append("\n");
            appendIndent(s, level, isIndented);
            s.append(":END:");

            hasUnderHead = true;
        }

        /* Just before content, with no new-line added. */

        s.append("\n");

        if (head.hasContent()) {
            /*
             * Separate content from header with an empty line,
             * unless it starts with following strings.
             * Until LOGBOOK and CLOCK support is added.
             */
            String content = head.getContent().trim();
            String firstLine = content.split("\n")[0].trim();
            if (!content.startsWith(":LOGBOOK:") && !content.startsWith("CLOCK: ")
                    && !isLogNoteHeading(content) && !lineStartswithDrawer(firstLine)) {
                if (settings.separateHeaderAndContentWithNewLine) {
                    s.append("\n");
                }
            }

//             s.append(head.getContent());
            s.append(head.getContent().replaceAll("(?m)^(\\*+\\s+)", " $1"));

            s.append("\n");

            if (settings.separateNotesWithNewLine == OrgParserSettings.SeparateNotesWithNewLine.MULTI_LINE_NOTES_ONLY) {
                s.append("\n");
            }

        } else {
            /* If planning times, properties or logbook exist, add an extra new-line. */
            if (hasUnderHead && settings.separateNotesWithNewLine == OrgParserSettings.SeparateNotesWithNewLine.MULTI_LINE_NOTES_ONLY) {
                s.append("\n");
            }
        }

        /* After note, on a new line. */

        if (settings.separateNotesWithNewLine == OrgParserSettings.SeparateNotesWithNewLine.ALWAYS) {
            s.append("\n");
        }

        return s.toString();
    }

    /** String the tags together, separated by colons. */
    private String tagsString(List<String> tags) {
        StringBuilder str = new StringBuilder();

        for (int i = 0; i < tags.size(); i++) {
            str.append(":").append(tags.get(i));
        }
        str.append(":");

        return str.toString();
    }

    private boolean isLogNoteHeading(String content) {
        for (String s: ORG_LOG_NOTE_HEADINGS) {
            if (content.startsWith("- " + s)) {
                return true;
            }
        }
        return false;
    }

    private boolean lineStartswithDrawer(String line) {
        return line.startsWith(":") && line.endsWith(":");
    }

    /** Append (level + 1) spaces. */
    private void appendIndent(StringBuilder s, int level, boolean isIndented) {
        if (isIndented) {
            for (int i = 0; i < level + 1; i++) {
                s.append(" ");
            }
        }
    }
}
