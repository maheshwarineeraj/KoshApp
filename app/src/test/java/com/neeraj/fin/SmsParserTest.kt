package com.neeraj.fin

import com.neeraj.fin.data.db.TxnType
import com.neeraj.fin.data.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression suite built from real-world messages that misparsed at some point. */
class SmsParserTest {

    @Test
    fun `multiline axis card spend parses amount not limit`() {
        val body = """
            Spent INR 50
            Axis Bank Card no. XX5740
            15-07-26 17:28:39 IST
            NARENDRA
            Avl Limit: INR 590932.3
            Not you? SMS BLOCK 5740 to 919951860002
        """.trimIndent()
        val p = SmsParser.parse("AX-AXISBK-S", body)
        assertNotNull("multiline Axis spend must parse", p)
        assertEquals(50_00L, p!!.amountMinor)
        assertEquals(TxnType.EXPENSE, p.type)
        assertEquals("5740", p.accountTail)
    }

    @Test
    fun `multiline axis spend with decimals`() {
        val body = "Spent INR 110\nAxis Bank Card no. XX5740\n15-07-26 17:21:55 IST\nKIRANDEVIWO\nAvl Limit: INR 590982.3\nNot you? SMS BLOCK 5740 to 919951860002"
        val p = SmsParser.parse("VK-AXISBK-S", body)
        assertNotNull(p)
        assertEquals(110_00L, p!!.amountMinor)
        assertEquals(TxnType.EXPENSE, p.type)
    }

    @Test
    fun `upi debit parses with vpa merchant`() {
        val p = SmsParser.parse("VM-HDFCBK", "Rs.500.00 debited from A/c XX1234 to VPA swiggy@ybl on 15-07-26. Ref 123456.")
        assertNotNull(p)
        assertEquals(500_00L, p!!.amountMinor)
        assertEquals("swiggy@ybl", p.merchant)
    }

    @Test
    fun `cc bill payment becomes transfer`() {
        val p = SmsParser.parse("VM-HDFCBK", "Alert! Paid Rs. 3,042.00 For: Credit Card payment From HDFC Bank A/c XX8694 Via Online Banking.")
        assertNotNull(p)
        assertEquals(TxnType.TRANSFER, p!!.type)
        assertEquals(3042_00L, p.amountMinor)
    }

    @Test
    fun `cc payment received confirmation is rejected`() {
        assertNull(
            SmsParser.parse(
                "AD-HDFCBK",
                "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 3042.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 6249 ON 3-7-2026.YOUR AVAILABLE LIMIT IS RS. 187000.00"
            )
        )
    }

    @Test
    fun `foreign currency spend uses foreign amount not inr limit`() {
        val p = SmsParser.parse(
            "JD-ICICIT",
            "USD 23.60 spent using ICICI Bank Card XX5004 on 29-Jun-26 on ANTHROPIC CLAU. Avl Limit: INR 3,62,771.71. If not you, call 18002662."
        )
        assertNotNull(p)
        assertEquals(23_60L, p!!.amountMinor)
        assertEquals("USD", p.foreignCurrency)
    }

    @Test
    fun `asba ipo block is rejected`() {
        assertNull(
            SmsParser.parse(
                "AX-IDFCFB",
                "Your ASBA application for SBIFUNDS is received and Application value of Rs 194012 is blocked in your registered Bank account on 14/07/2026."
            )
        )
    }

    @Test
    fun `otp is rejected`() {
        assertNull(SmsParser.parse("VM-HDFCBK", "Your OTP for transaction of Rs 5000 is 482913. Do not share it."))
    }

    @Test
    fun `personal sender is rejected`() {
        assertNull(SmsParser.parse("+919812345678", "I paid Rs 500 for the dinner yesterday"))
    }

    @Test
    fun `credit parses as income`() {
        val p = SmsParser.parse("VM-SBIINB", "Rs.25,000.00 credited to A/c XX9999 by NEFT from ACME CORP on 01-07-26.")
        assertNotNull(p)
        assertEquals(TxnType.INCOME, p!!.type)
        assertEquals(25_000_00L, p.amountMinor)
    }
}
