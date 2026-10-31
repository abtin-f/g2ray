package com.abtin.tglass.core.design

import androidx.compose.ui.unit.dp

/**
 * Shared layout constants in iOS points (1 dp == 1 pt, see ProvideUiScale). One name per role, so bars, buttons,
 * sheets and menus stay consistent across screens. Chat and chat-list internals keep their own measured values.
 */
object TgDimens {
    /** Height of the floating control row inside a GlassTopBar (UINavigationBar = 44). */
    val NavBarHeight = 44.dp
    /** Gap between the status bar and the control row. */
    val NavBarTopGap = 6.dp
    /** Side inset of the bar controls. */
    val NavBarSideInset = 12.dp
    /** Height of the progressive blur under the status bar + bar (added to the status bar inset). */
    val NavBarEdgeBlur = 76.dp
    /** Space reserved at both sides of the centered nav title for the glass buttons. */
    val NavTitleInset = 72.dp

    /** Round glass icon button / capsule glass text button height. */
    val GlassButton = 40.dp
    /** Larger glass pill (profile actions, media-viewer header). */
    val GlassPill = 44.dp
    /** Horizontal padding inside a text glass button. */
    val GlassTextPadding = 13.dp

    /** Filled primary button (Start Messaging, Continue). */
    val PrimaryButton = 50.dp
    /** Alert button. */
    val AlertButton = 48.dp
    /** Bottom action sheet row. */
    val SheetRow = 56.dp

    /** Context menu row and metrics. */
    val MenuRow = 44.dp
    val MenuWidth = 250.dp
    val MenuCorner = 26.dp
    val MenuHairlineInset = 20.dp

    /** Minimum list cell heights (UITableViewCell 44; with a subtitle 58). */
    val CellMin = 44.dp
    val CellMinSubtitle = 58.dp

    /** Segmented control height and chip-tab height. */
    val Segmented = 32.dp
    val ChipTab = 30.dp

    /** Toast distance below the status bar. */
    val ToastTop = 60.dp
}
