package pl.skypvp.market;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uklad (54 sloty):
 *
 *   kol. 1      : kategorie (lewa strona)
 *   kol. 2      : ozdobna ramka
 *   kol. 3-7    : oferty (5 kolumn x 5 rzedow = 25 na strone)
 *   kol. 8      : ozdobna ramka
 *   kol. 9      : (rzad 2) Twoje przedmioty, (rzad 3) Sortowanie, (rzad 4) Filtrowanie
 *   rzad 6      : wyczysc filtry | poprzednia | info | NASTEPNA | wyszukiwanie
 */
public class MarketGui extends BaseGui {

    private static final int[] CATEGORY_SLOTS = {0, 9, 18, 27, 36};
    private static final int SLOT_MINE = 17, SLOT_SORT = 26, SLOT_FILTER = 35;
    private static final int SLOT_RESET = 45, SLOT_PREV = 48, SLOT_INFO = 49, SLOT_NEXT = 50, SLOT_SEARCH = 52;
    private static final int[] ITEM_SLOTS = buildItemSlots();
    private static final int PER_PAGE = ITEM_SLOTS.length;

    private static int[] buildItemSlots() {
        int[] a = new int[25];
        int i = 0;
        for (int row = 0; row < 5; row++) {
            for (int col = 2; col <= 6; col++) a[i++] = row * 9 + col;
        }
        return a;
    }

    private final MarketManager m;
    private final Player player;
    private final ViewState st;
    private final Map<Integer, Long> slotIds = new HashMap<>();

    public MarketGui(MarketManager m, Player player) {
        this.m = m;
        this.player = player;
        this.st = m.state(player);

        List<Listing> list = m.query(player, st);
        int pages = pagesFor(list.size());
        st.page = Math.max(0, Math.min(st.page, pages - 1));

        this.inv = Bukkit.createInventory(this, 54,
                LegacyComponentSerializer.legacySection().deserialize(titleText(pages)));
        render(list, pages);
    }

    private static int pagesFor(int size) {
        return Math.max(1, (int) Math.ceil(size / (double) PER_PAGE));
    }

    private String titleText(int pages) {
        return m.cfg().getString("gui.title", "&8RYNEK &7({page}/{pages})")
                .replace("{page}", String.valueOf(st.page + 1))
                .replace("{pages}", String.valueOf(pages))
                .replace('&', '§');
    }

    /** Przelicza oferty, rysuje GUI od nowa i aktualizuje tytul okna (numer strony). */
    private void refresh() {
        List<Listing> list = m.query(player, st);
        int pages = pagesFor(list.size());
        st.page = Math.max(0, Math.min(st.page, pages - 1));
        render(list, pages);
        InventoryView view = player.getOpenInventory();
        if (view.getTopInventory().getHolder() == this) {
            view.setTitle(titleText(pages));
        }
    }

    private void render(List<Listing> list, int pages) {
        slotIds.clear();
        ItemStack filler = m.filler();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // --- oferty
        for (int i = 0; i < PER_PAGE; i++) {
            int slot = ITEM_SLOTS[i];
            int idx = st.page * PER_PAGE + i;
            if (idx < list.size()) {
                Listing l = list.get(idx);
                inv.setItem(slot, m.listingItem(l));
                slotIds.put(slot, l.id);
            } else {
                inv.setItem(slot, null);
            }
        }

        // --- kategorie (lewa strona)
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            Category c = cats[i];
            boolean sel = st.category == c;
            ItemStack it = m.item(c.icon, (sel ? "&a&l" : "&6&l") + c.label,
                    List.of(sel ? "&aWybrano &7(kliknij, aby odznaczyć)" : "&7Kliknij, aby filtrować"));
            if (sel) {
                ItemMeta meta = it.getItemMeta();
                meta.setEnchantmentGlintOverride(true);
                it.setItemMeta(meta);
            }
            inv.setItem(CATEGORY_SLOTS[i], it);
        }

        // --- prawa strona: Twoje przedmioty / Sortowanie / Filtrowanie
        inv.setItem(SLOT_MINE, m.button("my-items", Material.CHEST_MINECART, "&6&lTwoje przedmioty &7(Kliknij)",
                List.of("&7Aktywne oferty: &f" + m.countBy(player.getUniqueId()),
                        "&7Do odebrania: &f" + m.claims(player.getUniqueId()).size())));

        List<String> sortLore = new ArrayList<>();
        for (SortMode s : SortMode.values()) {
            sortLore.add(s == st.sort ? "&7✕ &a&n" + s.label : "&7✕ &c" + s.label);
        }
        inv.setItem(SLOT_SORT, m.button("sort", Material.HOPPER_MINECART, "&6&lSortowanie itemów &7(Kliknij)", sortLore));

        inv.setItem(SLOT_FILTER, m.button("filter", Material.FURNACE_MINECART, "&6&lFiltrowanie &7(Kliknij)", List.of(
                !st.affordable ? "&7✕ &a&nWyświetlaj wszystkie przedmioty" : "&7✕ &cWyświetlaj wszystkie przedmioty",
                st.affordable ? "&7✕ &a&nWyświetlaj tylko te, na które Cię stać" : "&7✕ &cWyświetlaj tylko te, na które Cię stać")));

        // --- dol: wyczysc / strony / info / wyszukiwanie
        inv.setItem(SLOT_RESET, m.item(Material.BARRIER, "&c&lWyczyść filtry",
                List.of("&7Wszystkie przedmioty, od najnowszych.")));

        if (st.page > 0) {
            inv.setItem(SLOT_PREV, m.item(Material.MAGMA_CREAM, "&cPoprzednia strona",
                    List.of("&7Strona &f" + st.page + "&7/&f" + pages)));
        }
        if (st.page < pages - 1) {
            inv.setItem(SLOT_NEXT, m.item(Material.SLIME_BALL, "&aNastępna strona",
                    List.of("&7Strona &f" + (st.page + 2) + "&7/&f" + pages)));
        }

        inv.setItem(SLOT_INFO, m.item(Material.PAPER,
                "&6" + (st.category == null ? "Wszystkie przedmioty" : st.category.label),
                List.of("&7Strona: &f" + (st.page + 1) + "/" + pages, "&7Ofert: &f" + list.size())));

        inv.setItem(SLOT_SEARCH, m.button("search", Material.OAK_SIGN, "&6&lWyszukiwanie &7(Kliknij)", List.of(
                "&7Aktualnie: &f" + (st.search == null ? "brak" : st.search),
                "",
                "&eLPM &7- wpisz nazwę (po polsku lub angielsku)",
                "&ePPM &7- wyczyść wyszukiwanie")));
    }

    @Override
    public void click(Player p, int slot, ClickType type) {
        Long id = slotIds.get(slot);
        if (id != null) {
            Listing l = m.visibleListing(id);
            if (l == null) {
                m.send(p, "gone");
                refresh();
                return;
            }
            if (l.seller.equals(p.getUniqueId())) {
                m.send(p, "own-item");
                return;
            }
            m.openConfirm(p, l);
            return;
        }

        boolean right = type == ClickType.RIGHT || type == ClickType.SHIFT_RIGHT;

        if (slot == SLOT_MINE) {
            sound(p);
            m.openMine(p);
        } else if (slot == SLOT_SORT) {
            sound(p);
            st.sort = right ? st.sort.prev() : st.sort.next();
            st.page = 0;
            refresh();
        } else if (slot == SLOT_FILTER) {
            sound(p);
            st.affordable = !st.affordable;
            st.page = 0;
            refresh();
        } else if (slot == SLOT_SEARCH) {
            sound(p);
            if (right) {
                st.search = null;
                st.page = 0;
                refresh();
            } else {
                m.openSearch(p);
            }
        } else if (slot == SLOT_RESET) {
            sound(p);
            st.reset();
            refresh();
        } else if (slot == SLOT_PREV && st.page > 0) {
            sound(p);
            st.page--;
            refresh();
        } else if (slot == SLOT_NEXT) {
            sound(p);
            st.page++;
            refresh();
        } else {
            for (int i = 0; i < CATEGORY_SLOTS.length; i++) {
                if (slot == CATEGORY_SLOTS[i]) {
                    sound(p);
                    Category c = Category.values()[i];
                    st.category = st.category == c ? null : c;
                    st.page = 0;
                    refresh();
                    return;
                }
            }
        }
    }

    private void sound(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1f);
    }
}
