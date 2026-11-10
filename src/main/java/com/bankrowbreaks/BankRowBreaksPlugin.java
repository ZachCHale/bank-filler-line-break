package com.bankrowbreaks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsPlugin;

@Slf4j
@PluginDescriptor(
    name = "Bank Filler Line Break",
    description = "Treat bank fillers as visual row breaks in normal bank tabs",
    tags = {"bank", "filler", "layout", "rows"}
)
public class BankRowBreaksPlugin extends Plugin
{
    @Inject BankRowBreaksConfig config;
    @Inject Client client;
    @Inject ClientThread clientThread;
    @Inject BankSearch bankSearch;
    @Inject PluginManager pluginManager;
    private boolean enabled;
    private boolean filteredBuild;
    private String lastStatus;

    @Provides
    BankRowBreaksConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(BankRowBreaksConfig.class);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (BankRowBreaksConfig.GROUP.equals(event.getGroup()))
        {
            clientThread.invokeLater(() -> {
                if (enabled) { bankSearch.layoutBank(); }
            });
        }
    }

    @Override
    protected void startUp()
    {
        enabled = true;
        lastStatus = null;
        log.info("Bank Filler Line Break 0.2.3 enabled");
        clientThread.invokeLater(bankSearch::layoutBank);
    }

    @Override
    protected void shutDown()
    {
        enabled = false;
        filteredBuild = false;
        clientThread.invokeLater(bankSearch::layoutBank);
    }

    @Subscribe
    public void onScriptCallbackEvent(ScriptCallbackEvent event)
    {
        if ("bankSearchFilter".equals(event.getEventName()))
        {
            int size = client.getObjectStackSize();
            if (size > 0)
            {
                Object filter = client.getObjectStack()[size - 1];
                filteredBuild |= filter instanceof String && !((String) filter).isEmpty();
            }
        }
    }

    @Subscribe(priority = -1)
    public void onScriptPreFired(ScriptPreFired event)
    {
        if (event.getScriptId() == ScriptID.BANKMAIN_BUILD)
        {
            filteredBuild = false;
        }
        if (enabled && event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
        {
            applyLayout();
        }
    }

    private void applyLayout()
    {
        int currentTab = client.getVarbitValue(VarbitID.BANK_CURRENTTAB);
        // Normal bank tabs are 0..9; e.g. potion storage uses a synthetic tab 15.
        if (filteredBuild || currentTab < 0 || currentTab > 9)
        {
            status("Skipped: search=" + filteredBuild + ", tab=" + currentTab);
            return;
        }
        for (Plugin plugin : pluginManager.getPlugins())
        {
            if (plugin instanceof BankTagsPlugin
                && ((BankTagsPlugin) plugin).getActiveBankTag() != null)
            {
                status("Skipped: active RuneLite bank tag");
                return;
            }
        }
        Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
        ItemContainer bank = client.getItemContainer(InventoryID.BANK);
        if (container == null || bank == null || container.getChildren() == null)
        {
            status("Skipped: bank widgets or item container unavailable");
            return;
        }
        List<Widget> items = new ArrayList<>();
        List<Widget> decorations = new ArrayList<>();
        boolean hasFiller = false;
        for (Widget child : container.getChildren())
        {
            if (child == null || child.isSelfHidden())
            {
                continue;
            }
            int index = child.getIndex();
            boolean emptySlot = child.getItemId() == ItemID.BLANKOBJECT;
            if (child.getItemId() >= 0 && child.getOriginalHeight() == RowLayout.ITEM_HEIGHT)
            {
                // Skip synthetic/reordered widgets belonging to other layouts.
                if (!emptySlot && (index < 0 || index >= bank.size() || bank.getItem(index) == null
                    || bank.getItem(index).getId() != child.getItemId()))
                {
                    status("Skipped: widget/bank mismatch at slot " + index);
                    return;
                }
                // The native bank leaves BLANKOBJECT widgets in vacated slots.
                // Keep their geometry in the grid: dropping them makes the next
                // item look like a new tab origin, pushing later columns right.
                // Move the blank widgets too so they cannot overlap moved items.
                items.add(child);
                hasFiller |= child.getItemId() == ItemID.BANK_FILLER;
            }
            else
            {
                decorations.add(child);
            }
        }
        boolean indentFirst = currentTab == 0 ? config.indentAllItems() : config.indentTabs();
        if (!hasFiller && !(config.indent() > 0 && (indentFirst || config.indentTabs())))
        {
            status("Skipped: no visible fillers among " + items.size() + " bank items");
            return;
        }
        items.sort(Comparator.comparingInt(Widget::getOriginalY)
            .thenComparingInt(Widget::getOriginalX));
        List<RowLayout.Cell> cells = new ArrayList<>();
        for (Widget item : items)
        {
            cells.add(new RowLayout.Cell(item.getOriginalX(), item.getOriginalY(),
                item.getItemId() == ItemID.BANK_FILLER));
        }
        // bankmain_finishbuilding has 22 int arguments. int13 is scroll height,
        // nine from the stack end, as in RuneLite Bank Tags' TabInterface.
        int stackSize = client.getIntStackSize();
        if (stackSize < 22)
        {
            status("Skipped: unexpected script stack size " + stackSize);
            return;
        }
        RowLayout.Result layout = RowLayout.arrange(cells, config.indent(), indentFirst,
            config.indentTabs(), config.blankRow(), config.ignoreConsecutiveFillers());
        for (int i = 0; i < items.size(); i++)
        {
            Widget item = items.get(i);
            RowLayout.Cell cell = layout.cells.get(i);
            item.setOriginalX(cell.x);
            item.setOriginalY(cell.y);
            item.revalidate();
        }
        for (Widget decoration : decorations)
        {
            int offset = layout.decorationOffset(decoration.getOriginalY());
            if (offset != 0)
            {
                decoration.setOriginalY(decoration.getOriginalY() + offset);
                decoration.revalidate();
            }
        }
        client.getIntStack()[stackSize - 9] += layout.extraHeight;
        status("Applied to " + items.size() + " items; extra height=" + layout.extraHeight
            + ", column spacing=" + RowLayout.columnSpacing(cells));
    }

    // Log only when the outcome changes; do not flood logs on bank rebuilds.
    private void status(String message)
    {
        if (!message.equals(lastStatus))
        {
            log.debug("Bank Filler Line Break: {}", message);
            lastStatus = message;
        }
    }
}
