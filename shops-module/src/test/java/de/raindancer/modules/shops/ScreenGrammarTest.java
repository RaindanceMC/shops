package de.raindancer.modules.shops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That the screens keep to one grammar — the same rules the claims, homes, warps, moderation and
 * mannequin modules keep to. Repeated deliberately; see {@code mannequin-module}'s own copy for why.
 */
class ScreenGrammarTest {

    private static final Path SCREENS = Path.of("src/main/java/de/raindancer/modules/shops/screen");

    private static final List<String> BASE_CLASSES = List.of("IShopsScreen", "Lines");

    private record Screen(String name, String body) {
    }

    private static List<Screen> screens() {
        try (Stream<Path> files = Files.list(SCREENS)) {
            List<Screen> found = new ArrayList<>();
            for (Path file : files.sorted().toList()) {
                found.add(new Screen(file.getFileName().toString().replace(".java", ""),
                        Files.readString(file)));
            }
            return found;
        } catch (IOException unreadable) {
            throw new AssertionError("could not read the screen package", unreadable);
        }
    }

    private static List<Screen> drawnScreens() {
        return screens().stream().filter(screen -> !BASE_CLASSES.contains(screen.name())).toList();
    }

    @Test
    @DisplayName("the scan found the screens, so a rename cannot quietly empty it")
    void theScanIsNotVacuous() {
        assertThat(screens()).extracting(Screen::name).contains(
                "IShopsScreen", "ShopFrontMenu", "ShopManageMenu", "StoreMenu", "ShopBrowserMenu");
        assertThat(drawnScreens()).isNotEmpty();
    }

    @Test
    @DisplayName("every screen that reads a right click says so on the button")
    void rightClicksAreAdvertised() {
        List<String> silent = new ArrayList<>();
        for (Screen screen : drawnScreens()) {
            String lowered = screen.body().toLowerCase(java.util.Locale.ROOT);
            if (screen.body().contains("isRightClick()") && !lowered.contains("right click")) {
                silent.add(screen.name());
            }
        }
        assertThat(silent)
                .as("these read a right click and never mention one in their lore, so nobody will find it")
                .isEmpty();
    }

    @Test
    @DisplayName("every screen that reads a shift click says so too")
    void shiftClicksAreAdvertised() {
        List<String> silent = new ArrayList<>();
        for (Screen screen : drawnScreens()) {
            if (screen.body().contains("isShiftClick()")
                    && !screen.body().toLowerCase(java.util.Locale.ROOT).contains("shift")) {
                silent.add(screen.name());
            }
        }
        assertThat(silent).as("these read a shift click without ever saying so").isEmpty();
    }

    @Test
    @DisplayName("every screen has a name for the diagnostic")
    void everyScreenDescribesItself() {
        List<String> silent = new ArrayList<>();
        for (Screen screen : drawnScreens()) {
            if (!screen.body().contains("describe()")) {
                silent.add(screen.name());
            }
        }
        assertThat(silent)
                .as("these never override describe(), so a diagnostic listing screens cannot name them")
                .isEmpty();
    }

    @Test
    @DisplayName("nothing irreversible happens without a confirmation")
    void theDangerSlotAlwaysConfirms() {
        List<String> unguarded = new ArrayList<>();
        for (Screen screen : drawnScreens()) {
            int at = screen.body().indexOf("danger(");
            while (at >= 0) {
                String call = screen.body().substring(at, Math.min(screen.body().length(), at + 900));
                if (!call.contains("ConfirmScreen")) {
                    unguarded.add(screen.name());
                }
                at = screen.body().indexOf("danger(", at + 1);
            }
        }
        assertThat(unguarded).as("a misclick on the danger slot must cost a page, not the shop").isEmpty();
    }

    @Test
    @DisplayName("screens build their buttons through Core's Icons")
    void nobodyBuildsTheirOwnItemStacks() {
        List<String> rolling = new ArrayList<>();
        for (Screen screen : screens()) {
            if (screen.body().contains("new ItemStack(") || screen.body().contains(".setItemMeta(new ")) {
                rolling.add(screen.name());
            }
        }
        assertThat(rolling).as("two ideas of what a button looks like").isEmpty();
    }

    @Test
    @DisplayName("a list says what an empty one means")
    void anEmptyListIsNotABlankWindow() {
        for (Screen screen : drawnScreens()) {
            if (screen.body().contains("extends PaginatedMenu")) {
                assertThat(screen.body()).as(screen.name()).contains("emptyIcon()");
            }
        }
    }

    @Test
    @DisplayName("buttons that open a page are spaced, not packed shoulder to shoulder")
    void nothingIsCrammedTogether() {
        // The claims module's rule, kept here too: the back room once had fifteen buttons in the order they
        // were written. Doors — buttons that open something — sit at least two columns apart, so a pane falls
        // between each pair; a control (a price typed, a switch) may sit beside its neighbour.
        java.util.regex.Pattern placed = java.util.regex.Pattern.compile("band\\(MenuLayout\\.([A-Z]+),\\s*(\\d+)");
        List<String> packed = new ArrayList<>();
        for (Screen screen : drawnScreens()) {
            java.util.Map<String, List<Integer>> byBand = new java.util.LinkedHashMap<>();
            java.util.regex.Matcher matcher = placed.matcher(screen.body());
            while (matcher.find()) {
                String rest = screen.body().substring(matcher.end(), Math.min(screen.body().length(), matcher.end() + 700));
                int nextButton = rest.indexOf("band(MenuLayout.");
                String thisButton = nextButton < 0 ? rest : rest.substring(0, nextButton);
                if (!thisButton.contains(".open()")) {
                    continue;
                }
                byBand.computeIfAbsent(matcher.group(1), band -> new ArrayList<>()).add(Integer.parseInt(matcher.group(2)));
            }
            byBand.forEach((band, columns) -> {
                List<Integer> sorted = new ArrayList<>(new java.util.TreeSet<>(columns));
                for (int at = 1; at < sorted.size(); at++) {
                    if (sorted.get(at) - sorted.get(at - 1) < 2) {
                        packed.add(screen.name() + " " + band + " columns " + sorted.get(at - 1) + "+" + sorted.get(at));
                    }
                }
            });
        }
        assertThat(packed).as("these put two doors in neighbouring columns — leave a pane between them").isEmpty();
    }

    @Test
    @DisplayName("the pages a command lands on explain themselves")
    void theFirstPagesHaveHelp() {
        for (String first : List.of("ShopBrowserMenu", "MyShopsMenu", "ShopFrontMenu", "ShopManageMenu")) {
            Screen screen = screens().stream().filter(found -> found.name().equals(first)).findFirst().orElseThrow();
            assertThat(screen.body()).as(first + " has no help button").contains("helpLines()");
        }
    }
}
