package com.stocksim;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.stocksim.exception.InsufficientSharesException;
import com.stocksim.exception.InvalidQuantityException;
import com.stocksim.model.Goal;
import com.stocksim.model.Holding;
import com.stocksim.model.PriceAlert;
import com.stocksim.util.DataCache;
import com.stocksim.util.Formats;
import com.stocksim.util.PasswordUtil;
import com.stocksim.util.Validator;

/** Pure unit tests: no database needed. Covers Unit 1 / Unit 2 building blocks. */
@DisplayName("Models, validation and utilities")
class UnitBasicsTest {

    // ---------------- Validator ----------------

    @Test
    void validatorAcceptsGoodInputAndRejectsBad() {
        assertTrue(Validator.isValidEmail("deepakshi@example.com"));
        assertFalse(Validator.isValidEmail("not-an-email"));
        assertFalse(Validator.isValidEmail("missing@domain"));

        assertTrue(Validator.isNotEmpty("abc"));
        assertFalse(Validator.isNotEmpty("   "));
        assertFalse(Validator.isNotEmpty(null));

        assertTrue(Validator.isValidPassword("password123"));
        assertFalse(Validator.isValidPassword("short"));

        assertTrue(Validator.isValidUsername("deepakshi_99"));
        assertFalse(Validator.isValidUsername("ab"));
        assertFalse(Validator.isValidUsername("has spaces"));

        assertTrue(Validator.isPositiveInteger("10"));
        assertFalse(Validator.isPositiveInteger("0"));
        assertFalse(Validator.isPositiveInteger("-5"));
        assertFalse(Validator.isPositiveInteger("3.5"));
        assertFalse(Validator.isPositiveInteger("abc"));

        assertTrue(Validator.isPositiveDouble("3500.50"));
        assertFalse(Validator.isPositiveDouble("-1"));
    }

    // ---------------- password hashing ----------------

    @Test
    void passwordsAreHashedWithSha256AndNeverStoredInPlainText() {
        String hash = PasswordUtil.hash("password123");

        assertEquals(64, hash.length(), "SHA-256 hex digest is 64 characters");
        assertNotEquals("password123", hash);
        assertEquals(hash, PasswordUtil.hash("password123"), "hashing is deterministic");
        assertNotEquals(hash, PasswordUtil.hash("password124"));

        assertTrue(PasswordUtil.matches("password123", hash));
        assertFalse(PasswordUtil.matches("wrongpassword", hash));
    }

    // ---------------- Holding: inheritance + interfaces ----------------

    @Test
    void holdingImplementsTheAbstractionsItDeclares() {
        Holding holding = new Holding(1, 1, 10, 100.0);
        assertInstanceOf(com.stocksim.model.FinancialEntity.class, holding);
        assertInstanceOf(com.stocksim.model.Calculable.class, holding);
        assertInstanceOf(com.stocksim.model.Tradable.class, holding);
    }

    @Test
    void holdingProfitAndLossMathIsCorrect() {
        Holding holding = new Holding(1, 1, 10, 100.0);

        assertEquals(1000.0, holding.getInvestedValue(), 0.001);
        assertEquals(1200.0, holding.getCurrentValue(120.0), 0.001);
        assertEquals(200.0, holding.getProfitLoss(120.0), 0.001);
        assertEquals(20.0, holding.getProfitLossPercent(120.0), 0.001);

        assertEquals(-250.0, holding.getProfitLoss(75.0), 0.001);
        assertEquals(-25.0, holding.getProfitLossPercent(75.0), 0.001);
    }

    @Test
    void buyingMoreSharesRecomputesTheWeightedAverageCost() throws Exception {
        Holding holding = new Holding(1, 1, 10, 100.0);
        holding.buy(10, 200.0);              // 10 @ 100 + 10 @ 200 -> 20 @ 150

        assertEquals(20, holding.getQuantity());
        assertEquals(150.0, holding.getAverageBuyPrice(), 0.001);
        assertEquals(3000.0, holding.getInvestedValue(), 0.001);
    }

    @Test
    void sellingReducesQuantityButLeavesAverageCostAlone() throws Exception {
        Holding holding = new Holding(1, 1, 20, 150.0);
        holding.sell(5);

        assertEquals(15, holding.getQuantity());
        assertEquals(150.0, holding.getAverageBuyPrice(), 0.001);
    }

    @Test
    void holdingRejectsBadQuantities() {
        Holding holding = new Holding(1, 1, 10, 100.0);

        assertThrows(InvalidQuantityException.class, () -> holding.buy(0, 100.0));
        assertThrows(InvalidQuantityException.class, () -> holding.sell(-1));
        assertThrows(InsufficientSharesException.class, () -> holding.sell(11));
    }

    // ---------------- Goal ----------------

    @Test
    void goalProgressIsClampedBetweenZeroAndOne() {
        Goal goal = new Goal(1, "Reach 1,50,000", 150000.0);

        assertEquals(0.0, goal.getProgress(0.0), 0.001);
        assertEquals(0.5, goal.getProgress(75000.0), 0.001);
        assertEquals(1.0, goal.getProgress(150000.0), 0.001);
        assertEquals(1.0, goal.getProgress(999999.0), 0.001, "never exceeds 1.0 for a ProgressBar");

        assertEquals(50.0, goal.getProgressPercent(75000.0), 0.001);
        assertFalse(goal.isAchieved(149999.0));
        assertTrue(goal.isAchieved(150000.0));
        assertInstanceOf(com.stocksim.model.FinancialEntity.class, goal);
    }

    // ---------------- PriceAlert ----------------

    @Test
    void alertConditionsFireOnlyWhenTheyShould() {
        PriceAlert above = new PriceAlert(1, 1, PriceAlert.Condition.ABOVE, 3500.0);
        assertFalse(above.isConditionMet(3499.99));
        assertTrue(above.isConditionMet(3500.0));
        assertTrue(above.isConditionMet(3600.0));

        PriceAlert below = new PriceAlert(1, 1, PriceAlert.Condition.BELOW, 3000.0);
        assertFalse(below.isConditionMet(3000.01));
        assertTrue(below.isConditionMet(3000.0));
        assertTrue(below.isConditionMet(2900.0));

        assertEquals("Active", above.getStatus());
        above.setTriggered(true);
        assertEquals("Triggered", above.getStatus());
    }

    // ---------------- generics ----------------

    @Test
    void genericDataCacheStoresAndClears() {
        DataCache<String> cache = new DataCache<>();
        assertTrue(cache.isEmpty());

        cache.put("TCS", "Tata Consultancy Services");
        cache.put("INFY", "Infosys");

        assertEquals(2, cache.size());
        assertEquals("Infosys", cache.get("INFY"));
        assertTrue(cache.contains("TCS"));
        assertEquals(2, cache.getAll().size());

        cache.remove("TCS");
        assertEquals(1, cache.size());
        cache.clear();
        assertTrue(cache.isEmpty());
    }

    // ---------------- formatting ----------------

    @Test
    void moneyIsFormattedWithIndianDigitGrouping() {
        assertEquals("1,00,000.00", Formats.plain(100000.0));
        assertEquals("1,50,000.00", Formats.plain(150000.0));
        assertEquals("999.50", Formats.plain(999.5));
        assertEquals("12,345.67", Formats.plain(12345.67));
        assertEquals("1,23,45,678.00", Formats.plain(12345678.0));

        assertTrue(Formats.money(100000.0).startsWith(Formats.RUPEE));
        assertEquals("+12.50%", Formats.signedPercent(12.5));
        assertEquals("-3.20%", Formats.signedPercent(-3.2));

        assertEquals("positive", Formats.signClass(1.0));
        assertEquals("negative", Formats.signClass(-1.0));
        assertEquals("neutral", Formats.signClass(0.0));
    }
}
