package com.bankrowbreaks;

import java.util.Collections;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BankRowBreaksPluginTest
{
    private BankRowBreaksPlugin plugin;
    private Client client;
    private Widget[] widgets;
    private int[] stack;

    @Before public void setup()
    {
        plugin = new BankRowBreaksPlugin();
        plugin.config = mock(BankRowBreaksConfig.class);
        client = mock(Client.class);
        plugin.client = client;
        plugin.clientThread = mock(ClientThread.class);
        plugin.bankSearch = mock(BankSearch.class);
        plugin.pluginManager = mock(PluginManager.class);
        when(plugin.pluginManager.getPlugins()).thenReturn(Collections.emptyList());
        Widget container = mock(Widget.class);
        ItemContainer bank = mock(ItemContainer.class);
        when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(container);
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(bank);
        when(bank.size()).thenReturn(8);
        widgets = new Widget[8];
        for (int i = 0; i < widgets.length; i++)
        {
            int id = i == 3 ? ItemID.BANK_FILLER : 100 + i;
            widgets[i] = mock(Widget.class);
            when(widgets[i].getIndex()).thenReturn(i);
            when(widgets[i].getItemId()).thenReturn(id);
            when(widgets[i].getOriginalHeight()).thenReturn(32);
            when(widgets[i].getOriginalX()).thenReturn(51 + i * 48);
            when(bank.getItem(i)).thenReturn(new Item(id, 1));
        }
        when(container.getChildren()).thenReturn(widgets);
        stack = new int[32];
        stack[13] = 36;
        when(client.getIntStack()).thenReturn(stack);
        when(client.getIntStackSize()).thenReturn(22);
        plugin.startUp();
        fire(ScriptID.BANKMAIN_BUILD);
    }

    private void fire(int id)
    {
        ScriptPreFired event = mock(ScriptPreFired.class);
        when(event.getScriptId()).thenReturn(id);
        plugin.onScriptPreFired(event);
    }

    private void filter(String text)
    {
        when(client.getObjectStack()).thenReturn(new Object[] {text});
        when(client.getObjectStackSize()).thenReturn(1);
        ScriptCallbackEvent event = mock(ScriptCallbackEvent.class);
        when(event.getEventName()).thenReturn("bankSearchFilter");
        plugin.onScriptCallbackEvent(event);
    }

    @Test public void normalBuildMovesWidgetsAndExtendsScrollHeight()
    {
        filter("");
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4]).setOriginalX(51);
        verify(widgets[4]).setOriginalY(36);
        verify(widgets[7]).setOriginalY(36);
        verify(widgets[4], never()).setItemId(anyInt());
        assertEquals(72, stack[13]);
    }

    @Test public void searchIsUntouchedAndNextBuildResetsFilter()
    {
        filter("rune");
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4], never()).setOriginalY(anyInt());
        assertEquals(36, stack[13]);
        fire(ScriptID.BANKMAIN_BUILD);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4]).setOriginalY(36);
    }

    @Test public void rememberedFillerSelectionDoesNotDisableRowBreaks()
    {
        // This is a persistent selection, not an editing-mode boolean.
        when(client.getVarbitValue(VarbitID.BANK_FILLERMODE)).thenReturn(1);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4]).setOriginalY(36);
        assertEquals(72, stack[13]);
    }

    @Test public void potionStorageIsUntouched()
    {
        when(client.getVarbitValue(VarbitID.BANK_CURRENTTAB)).thenReturn(15);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4], never()).setOriginalY(anyInt());
    }

    @Test public void syntheticWidgetsAreUntouched()
    {
        when(widgets[0].getItemId()).thenReturn(999);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4], never()).setOriginalY(anyInt());
    }

    @Test public void shutdownStopsApplyingLayout()
    {
        plugin.shutDown();
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4], never()).setOriginalY(anyInt());
    }
    @Test public void indentAndBlankRowCombineAfterFiller()
    {
        when(plugin.config.indent()).thenReturn(2);
        when(plugin.config.blankRow()).thenReturn(true);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4]).setOriginalX(147);
        verify(widgets[4]).setOriginalY(72);
    }

    @Test public void firstRowOptionsAreIndependentWithoutFillers()
    {
        when(widgets[3].getItemId()).thenReturn(103);
        when(client.getItemContainer(InventoryID.BANK).getItem(3)).thenReturn(new Item(103, 1));
        when(plugin.config.indent()).thenReturn(2);
        when(plugin.config.indentTabs()).thenReturn(true);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[0]).setOriginalX(51);
        clearInvocations(widgets[0]);
        when(client.getVarbitValue(VarbitID.BANK_CURRENTTAB)).thenReturn(1);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[0]).setOriginalX(147);
        clearInvocations(widgets[0]);
        when(client.getVarbitValue(VarbitID.BANK_CURRENTTAB)).thenReturn(0);
        when(plugin.config.indentTabs()).thenReturn(false);
        when(plugin.config.indentAllItems()).thenReturn(true);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[0]).setOriginalX(147);
    }
    @Test public void optionChangeRebuildsBankOnClientThread()
    {
        clearInvocations(plugin.clientThread);
        net.runelite.client.events.ConfigChanged event = mock(net.runelite.client.events.ConfigChanged.class);
        when(event.getGroup()).thenReturn(BankRowBreaksConfig.GROUP);
        plugin.onConfigChanged(event);
        org.mockito.ArgumentCaptor<Runnable> task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(plugin.clientThread).invokeLater(task.capture());
        verify(plugin.bankSearch, never()).layoutBank();
        task.getValue().run();
        verify(plugin.bankSearch).layoutBank();
    }

    @Test public void defaultsPreserveExistingBehavior()
    {
        BankRowBreaksConfig defaults = new BankRowBreaksConfig() {};
        assertEquals(0, defaults.indent());
        assertFalse(defaults.indentTabs());
        assertFalse(defaults.indentAllItems());
        assertFalse(defaults.blankRow());
        assertTrue(defaults.ignoreConsecutiveFillers());
    }
    @Test public void consecutiveFillerSettingIsAppliedToBankWidgets()
    {
        when(widgets[2].getItemId()).thenReturn(ItemID.BANK_FILLER);
        when(client.getItemContainer(InventoryID.BANK).getItem(2)).thenReturn(new Item(ItemID.BANK_FILLER, 1));
        when(plugin.config.ignoreConsecutiveFillers()).thenReturn(true);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4]).setOriginalX(99);
        verify(widgets[4]).setOriginalY(36);
        clearInvocations(widgets[4]);
        when(plugin.config.ignoreConsecutiveFillers()).thenReturn(false);
        fire(ScriptID.BANKMAIN_FINISHBUILDING);
        verify(widgets[4]).setOriginalX(51);
        verify(widgets[4]).setOriginalY(72);
    }
}
