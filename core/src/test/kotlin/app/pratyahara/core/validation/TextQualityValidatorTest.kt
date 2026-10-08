package app.pratyahara.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextQualityValidatorTest {
    private val reason = TextQualityValidator.budgetReason
    private val task = TextQualityValidator.nightlyTask

    private fun ok(v: TextQualityValidator, s: String) = assertEquals(s, ValidationResult.Ok, v.validate(s))
    private fun bad(v: TextQualityValidator, s: String) = assertTrue(s, v.validate(s) is ValidationResult.Invalid)

    @Test fun `accepts a genuine two-sentence reason`() {
        ok(reason, "My sister sent me a series of recipe reels for her wedding menu. I need to watch them tonight and reply with my picks before Friday.")
    }

    @Test fun `rejects empty, single words and short text`() {
        bad(reason, "")
        bad(reason, "   ")
        bad(reason, "reels")
        bad(reason, "I want to watch more reels today.")
    }

    @Test fun `rejects one long sentence`() {
        bad(reason, "I would like to have a little more time on reels today because there are some videos my friends sent me that I want to see")
    }

    @Test fun `rejects repeated characters`() {
        bad(reason, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        bad(reason, "I need more time today!!!!!!!! Because I want it. Really really want it, ok? Sure fine whatever then.")
    }

    @Test fun `rejects lazy phrases padded with filler`() {
        bad(reason, "just bored")
        bad(reason, "idk. need it.")
        bad(task, "idk")
        bad(task, "whatever")
    }

    @Test fun `rejects the same words over and over`() {
        bad(reason, "reels reels reels reels reels reels. reels reels reels reels reels reels reels. reels reels reels reels reels reels.")
    }

    @Test fun `rejects keyboard mashing`() {
        bad(reason, "sdfg hjkl qwrt zxcv bnm. sdfg hjkl qwrt zxcv bnm ghjk. fdsg trwq vcxz mnbv lkjh gfds plmk qwrt bnm zxcv.")
    }

    @Test fun `a lazy phrase inside a real reason is fine`() {
        ok(reason, "I'm not just bored today. My cousin posted the reels from her graduation and I promised I would watch every one of them tonight.")
    }

    @Test fun `accepts a short concrete task`() {
        ok(task, "Call the bank before noon")
        ok(task, "Finish chapter 3 of the thesis")
        ok(task, "माँ को फ़ोन करना है शाम को")
    }

    @Test fun `rejects vague or tiny tasks`() {
        bad(task, "gym")
        bad(task, "work")
        bad(task, "do stuff")
    }
}
