package com.example.roombooking

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.roombooking.ui.common.GradientTitle
import com.example.roombooking.ui.common.LabelValue
import com.example.roombooking.ui.common.TimeDropdown
import com.example.roombooking.ui.theme.RoomBookingTheme
import org.junit.Rule
import org.junit.Test

/** What TalkBack is told, which screenshots can't show. */
class AccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sectionTitleIsAHeading() {
        rule.setContent { RoomBookingTheme { GradientTitle("สรุป") } }
        rule.onNodeWithText("สรุป").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test fun labelAndValueAreReadTogether() {
        rule.setContent { RoomBookingTheme { LabelValue("อาคาร", "หอสมุด") } }
        rule.onNodeWithText("อาคาร", useUnmergedTree = false).assertTextContains("หอสมุด")
    }

    @Test fun timeDropdownOpensAndShowsTheChoice() {
        rule.setContent {
            RoomBookingTheme {
                var minute by remember { mutableStateOf<Int?>(null) }
                TimeDropdown("เริ่ม", minute, { minute = it })
            }
        }
        // announced as a drop-down that can't be typed into, not as a plain button
        rule.onNodeWithText("เริ่ม")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))
            .assertHasClickAction()
            .performClick()
        rule.onNodeWithText("09:00").performClick()
        rule.onNodeWithText("เริ่ม").assertTextContains("09:00")
    }
}
