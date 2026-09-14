package com.robotkinematicslab.mobile.ui.editor

data class DhInputRow(
    val jointTypeText: String = "REVOLUTE",
    val thetaText: String = "0",
    val dText: String = "0",
    val aText: String = "0",
    val alphaText: String = "0",
    val minText: String = "-180",
    val maxText: String = "180",
    val homeText: String = "0"
)