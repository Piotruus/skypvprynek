package pl.skypvp.market;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ConfirmGui extends BaseGui {

    private final MarketManager m;
    private final long id;

    public ConfirmGui(MarketManager m, Listing l) {
        this.m = m;
        this.id = l.id;
        this.inv = Bukkit.createInventory(this, 27, m.c("&0Potwierdź zakup"));
        ItemStack filler = m.filler();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);
        inv.setItem(11, m.item(Material.LIME_CONCRETE, "&a&lKUP",
                List.of("&7Cena: &a" + Util.formatPrice(l.price))));
        inv.setItem(13, l.item.clone());
        inv.setItem(15, m.item(Material.RED_CONCRETE, "&c&lANULUJ", null));
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        if (slot == 11) {
            p.closeInventory();
            m.buy(p, id);
        } else if (slot == 15) {
            m.openMarket(p);
        }
    }
}
