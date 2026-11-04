package com.bankrowbreaks;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(BankRowBreaksConfig.GROUP)
public interface BankRowBreaksConfig extends Config
{
    String GROUP = "bank-row-breaks";

    @Range(min = 0, max = 7)
    @ConfigItem(keyName = "indent", name = "Indent", description = "Empty slots before the first item after a filler (0–7). Also used by first-row indentation.", position = 0)
    default int indent() { return 0; }

    @ConfigItem(keyName = "indentTabs", name = "Indent tab first rows", description = "Indent the first row of normal tabs, including their sections in All Items.", position = 1)
    default boolean indentTabs() { return false; }

    @ConfigItem(keyName = "indentAllItems", name = "Indent All Items first row", description = "Separately indent the very first row of the All Items view.", position = 2)
    default boolean indentAllItems() { return false; }

    @ConfigItem(keyName = "blankRow", name = "Blank row between sections", description = "Leave a completely empty row after each filler before placing the following items.", position = 3)
    default boolean blankRow() { return false; }
    @ConfigItem(keyName = "ignoreConsecutiveFillers", name = "Ignore consecutive fillers", description = "Only the first filler in a consecutive run creates a row break. Remaining fillers stay visible and occupy ordinary slots.", position = 4)
    default boolean ignoreConsecutiveFillers() { return true; }
}
