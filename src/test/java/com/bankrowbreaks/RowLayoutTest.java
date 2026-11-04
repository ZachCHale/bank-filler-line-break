package com.bankrowbreaks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class RowLayoutTest
{
    private List<RowLayout.Cell> grid(int count, int... fillers)
    {
        List<RowLayout.Cell> cells = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            boolean filler = false;
            for (int f : fillers) { filler |= f == i; }
            cells.add(new RowLayout.Cell(51 + i % 8 * 48, i / 8 * 36, filler));
        }
        return cells;
    }

    @Test public void emptyBank()
    {
        RowLayout.Result r = RowLayout.arrange(grid(0));
        assertTrue(r.cells.isEmpty());
        assertEquals(0, r.extraHeight);
    }

    @Test public void ordinaryBankUnchanged()
    {
        List<RowLayout.Cell> input = grid(100);
        RowLayout.Result r = RowLayout.arrange(input);
        for (int i = 0; i < input.size(); i++)
        {
            assertEquals(input.get(i).x, r.cells.get(i).x);
            assertEquals(input.get(i).y, r.cells.get(i).y);
        }
        assertEquals(0, r.extraHeight);
    }

    @Test public void middleFillerWrapsNextItem()
    {
        RowLayout.Result r = RowLayout.arrange(grid(8, 3));
        assertEquals(195, r.cells.get(3).x);
        assertEquals(0, r.cells.get(3).y);
        assertEquals(51, r.cells.get(4).x);
        assertEquals(36, r.cells.get(4).y);
        assertEquals(36, r.extraHeight);
    }

    @Test public void lastColumnDoesNotSkipExtraRow()
    {
        RowLayout.Result r = RowLayout.arrange(grid(9, 7));
        assertEquals(51, r.cells.get(8).x);
        assertEquals(36, r.cells.get(8).y);
        assertEquals(0, r.extraHeight);
    }

    @Test public void firstColumnAndConsecutiveFillers()
    {
        RowLayout.Result r = RowLayout.arrange(grid(4, 0, 1));
        assertEquals(0, r.cells.get(0).y);
        assertEquals(36, r.cells.get(1).y);
        assertEquals(72, r.cells.get(2).y);
        assertEquals(51, r.cells.get(2).x);
        assertEquals(99, r.cells.get(3).x);
    }

    @Test public void trailingFillerDoesNotAddEmptyRow()
    {
        assertEquals(0, RowLayout.arrange(grid(4, 3)).extraHeight);
    }

    @Test public void laterTabAndHeaderShiftTogether()
    {
        List<RowLayout.Cell> input = grid(8, 2);
        input.add(new RowLayout.Cell(51, 76, false));
        input.add(new RowLayout.Cell(99, 76, false));
        RowLayout.Result r = RowLayout.arrange(input);
        assertEquals(112, r.cells.get(8).y);
        assertEquals(36, r.decorationOffset(50));
        assertEquals(0, r.decorationOffset(0));
        assertEquals(36, r.extraHeight);
    }

    @Test public void partialTabPreservesNextTabBoundary()
    {
        List<RowLayout.Cell> input = grid(4, 1);
        input.add(new RowLayout.Cell(51, 76, false));
        RowLayout.Result r = RowLayout.arrange(input);
        assertEquals(36, r.cells.get(2).y);
        assertEquals(112, r.cells.get(4).y);
    }

    @Test public void repeatedBuildFromNativeGeometryDoesNotAccumulate()
    {
        List<RowLayout.Cell> input = grid(30, 2, 15);
        RowLayout.Result a = RowLayout.arrange(input);
        RowLayout.Result b = RowLayout.arrange(input);
        assertEquals(a.extraHeight, b.extraHeight);
        for (int i = 0; i < input.size(); i++)
        {
            assertEquals(a.cells.get(i).x, b.cells.get(i).x);
            assertEquals(a.cells.get(i).y, b.cells.get(i).y);
        }
    }

    @Test public void randomizedBanksPreserveOrderAndBreaks()
    {
        Random random = new Random(12345);
        for (int trial = 0; trial < 500; trial++)
        {
            List<RowLayout.Cell> input = grid(1 + random.nextInt(1000));
            for (int i = 0; i < input.size(); i++)
            {
                RowLayout.Cell c = input.get(i);
                input.set(i, new RowLayout.Cell(c.x, c.y, random.nextInt(5) == 0));
            }
            RowLayout.Result r = RowLayout.arrange(input);
            int previous = -1;
            for (int i = 0; i < r.cells.size(); i++)
            {
                RowLayout.Cell c = r.cells.get(i);
                int slot = c.y / 36 * 8 + (c.x - 51) / 48;
                assertTrue(slot > previous);
                assertEquals(input.get(i).filler, c.filler);
                if (i > 0 && input.get(i - 1).filler)
                {
                    assertEquals(51, c.x);
                    assertEquals((previous / 8 + 1) * 36, c.y);
                }
                previous = slot;
            }
            int oldBottom = input.get(input.size() - 1).y + 32;
            int newBottom = r.cells.get(r.cells.size() - 1).y + 32;
            assertEquals(newBottom - oldBottom, r.extraHeight);
        }
    }
    @Test public void widerBankStillWrapsAfterFiller()
    {
        for (int spacing : new int[] {42, 49, 54, 60, 72})
        {
            List<RowLayout.Cell> input = new ArrayList<>();
            for (int i = 0; i < 16; i++)
            {
                input.add(new RowLayout.Cell(51 + i % 8 * spacing, i / 8 * 36, i == 3));
            }
            RowLayout.Result r = RowLayout.arrange(input);
            assertEquals(spacing, RowLayout.columnSpacing(input));
            assertEquals(51, r.cells.get(4).x);
            assertEquals(36, r.cells.get(4).y);
            assertEquals(51 + spacing, r.cells.get(5).x);
            assertEquals(36, r.extraHeight);
        }
    }
    @Test public void indentClampsAndOnlyAffectsSectionStarts()
    {
        for (int requested : new int[] {-20, 0, 1, 7, 99})
        {
            int indent = Math.max(0, Math.min(7, requested));
            RowLayout.Result r = RowLayout.arrange(grid(20, 3), requested, false, false, false);
            assertEquals(51, r.cells.get(0).x);
            assertEquals(51 + indent * 48, r.cells.get(4).x);
            assertEquals(36, r.cells.get(4).y);
            assertEquals(51, r.cells.get(12 - indent).x);
            assertEquals(72, r.cells.get(12 - indent).y);
        }
    }

    @Test public void blankRowAfterLastColumnFillerAndNoTrailingSpace()
    {
        RowLayout.Result r = RowLayout.arrange(grid(9, 7), 7, false, false, true);
        assertEquals(387, r.cells.get(8).x);
        assertEquals(72, r.cells.get(8).y);
        assertEquals(36, r.extraHeight);
        assertEquals(0, RowLayout.arrange(grid(4, 3), 7, false, false, true).extraHeight);
    }

    @Test public void firstAndLaterTabIndentsAreIndependent()
    {
        List<RowLayout.Cell> input = grid(8);
        input.add(new RowLayout.Cell(51, 76, false));
        RowLayout.Result first = RowLayout.arrange(input, 2, true, false, false);
        assertEquals(147, first.cells.get(0).x);
        assertEquals(51, first.cells.get(8).x);
        assertEquals(112, first.cells.get(8).y);
        assertEquals(36, first.decorationOffset(50));
        RowLayout.Result later = RowLayout.arrange(input, 2, false, true, false);
        assertEquals(51, later.cells.get(0).x);
        assertEquals(147, later.cells.get(8).x);
        assertEquals(76, later.cells.get(8).y);
    }
    @Test public void consecutiveFillersOnlyBreakOnceWithIndentAndBlankRow()
    {
        RowLayout.Result r = RowLayout.arrange(grid(6, 1, 2, 3), 2, false, false, true, true);
        assertEquals(72, r.cells.get(2).y);
        assertEquals(147, r.cells.get(2).x);
        assertEquals(72, r.cells.get(3).y);
        assertEquals(195, r.cells.get(3).x);
        assertEquals(72, r.cells.get(4).y);
        assertEquals(243, r.cells.get(4).x);
    }

    @Test public void longFillerRunsWrapNaturallyAndNewRunBreaksAgain()
    {
        RowLayout.Result r = RowLayout.arrange(grid(15, 0,1,2,3,4,5,6,7,8,9,11,12), 0, false, false, false, true);
        assertEquals(36, r.cells.get(8).y);
        assertEquals(72, r.cells.get(9).y);
        assertEquals(108, r.cells.get(12).y);
        assertEquals(108, r.cells.get(13).y);
        assertEquals(72, r.extraHeight);
    }

    @Test public void disablingIgnoreRestoresEveryFillerBreak()
    {
        RowLayout.Result r = RowLayout.arrange(grid(4, 0,1,2), 0, false, false, false, false);
        assertEquals(108, r.cells.get(3).y);
    }

    @Test public void consecutiveRunResetsAtTabBoundary()
    {
        List<RowLayout.Cell> input = grid(2, 1);
        input.add(new RowLayout.Cell(51, 76, true));
        input.add(new RowLayout.Cell(99, 76, false));
        RowLayout.Result r = RowLayout.arrange(input, 0, false, false, false, true);
        assertEquals(112, r.cells.get(3).y);
    }
}
