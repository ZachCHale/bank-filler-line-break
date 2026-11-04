package com.bankrowbreaks;

import java.util.ArrayList;
import java.util.List;

/** Input is native bank geometry, never the previous output. */
final class RowLayout
{
    static final int COLUMNS = 8;
    static final int X_STEP = 48;
    static final int Y_STEP = 36;
    static final int ITEM_HEIGHT = 32;

    static final class Cell
    {
        final int x;
        final int y;
        final boolean filler;
        Cell(int x, int y, boolean filler)
        {
            this.x = x;
            this.y = y;
            this.filler = filler;
        }
    }

    static final class Result
    {
        final List<Cell> cells = new ArrayList<>();
        final List<int[]> boundaries = new ArrayList<>();
        int extraHeight;
        int decorationOffset(int originalY)
        {
            int offset = 0;
            for (int[] boundary : boundaries)
            {
                if (originalY >= boundary[0])
                {
                    offset = boundary[1];
                }
            }
            return offset;
        }
    }

    static int columnSpacing(List<Cell> input)
    {
        int spacing = Integer.MAX_VALUE;
        for (int i = 1; i < input.size(); i++)
        {
            Cell previous = input.get(i - 1);
            Cell current = input.get(i);
            int difference = current.x - previous.x;
            if (current.y == previous.y && difference > 0)
            {
                spacing = Math.min(spacing, difference);
            }
        }
        return spacing == Integer.MAX_VALUE ? X_STEP : spacing;
    }

    static Result arrange(List<Cell> input)
    {
        return arrange(input, 0, false, false, false);
    }

    static Result arrange(List<Cell> input, int requestedIndent, boolean indentFirst,
        boolean indentLaterTabs, boolean blankRow)
    {
        return arrange(input, requestedIndent, indentFirst, indentLaterTabs, blankRow, false);
    }

    static Result arrange(List<Cell> input, int requestedIndent, boolean indentFirst,
        boolean indentLaterTabs, boolean blankRow, boolean ignoreConsecutiveFillers)
    {
        int indent = Math.max(0, Math.min(7, requestedIndent));
        int xStep = columnSpacing(input);
        Result result = new Result();
        int start = 0;
        while (start < input.size())
        {
            Cell first = input.get(start);
            int end = start + 1;
            // Tab headings and partial final rows separate native grid segments.
            while (end < input.size())
            {
                int index = end - start;
                Cell next = input.get(end);
                if (next.x != first.x + index % COLUMNS * xStep
                    || next.y != first.y + index / COLUMNS * Y_STEP)
                {
                    break;
                }
                end++;
            }
            int slot = (start == 0 ? indentFirst : indentLaterTabs) ? indent : 0;
            int lastSlot = 0;
            for (int i = start; i < end; i++)
            {
                Cell cell = input.get(i);
                lastSlot = slot;
                result.cells.add(new Cell(first.x + slot % COLUMNS * xStep,
                    first.y + result.extraHeight + slot / COLUMNS * Y_STEP, cell.filler));
                slot++;
                if (cell.filler && (!ignoreConsecutiveFillers || i == start || !input.get(i - 1).filler))
                {
                    // Column eight already ends the row.
                    slot = (slot + COLUMNS - 1) / COLUMNS * COLUMNS
                        + (blankRow ? COLUMNS : 0) + indent;
                }
            }
            int oldRows = (end - start + COLUMNS - 1) / COLUMNS;
            int newRows = lastSlot / COLUMNS + 1;
            result.extraHeight += (newRows - oldRows) * Y_STEP;
            result.boundaries.add(new int[] {
                input.get(end - 1).y + ITEM_HEIGHT, result.extraHeight
            });
            start = end;
        }
        return result;
    }
}
