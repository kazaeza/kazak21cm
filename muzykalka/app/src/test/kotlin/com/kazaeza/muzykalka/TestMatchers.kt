package com.kazaeza.muzykalka

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/** A node whose tap action carries [label] (`clickable(onClickLabel = …)`): the chord tiles say «Послушать аккорд». */
fun hasClickLabel(label: String) = SemanticsMatcher("has click label «$label»") { node ->
    node.config.getOrNull(SemanticsActions.OnClick)?.label == label
}
