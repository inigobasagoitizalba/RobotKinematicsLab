package com.robotkinematicslab.mobile.ui.dataset.robotstore

import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import com.robotkinematicslab.mobile.R
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets

/**
 * A commercial identity is admitted here only when two independent primary-evidence gates pass:
 * a complete manufacturer manual for one fixed variant and an explicit manufacturer kinematic
 * source (DH table or equivalent model). Artwork is presentation only and can never satisfy either
 * gate. This catalog deliberately remains separate from dataset generation until its exact frame
 * convention is imported and verified with forward-kinematics landmarks.
 */
internal data class IndustrialRobotReference(
    val id: String,
    val displayName: String,
    val fixedVariant: String,
    val morphology: String,
    @DrawableRes val artworkResId: Int,
    val evidenceStatus: RobotReferenceEvidenceStatus,
    val specifications: List<RobotReferenceSpecification>,
    val jointLimits: List<RobotReferenceJointLimit>,
    val documents: List<RobotReferenceDocument>,
    val kinematicEvidence: RobotKinematicEvidence,
    val artworkEvidence: RobotArtworkEvidence
)

internal data class RobotReferenceSpecification(val label: String, val value: String)

internal data class RobotReferenceJointLimit(val joint: String, val range: String)

internal enum class RobotReferenceDocumentKind(val label: String) {
    USER_MANUAL("User manual"),
    HARDWARE_MANUAL("Hardware manual"),
    OPERATING_MANUAL("Operating manual")
}

internal enum class RobotManualRedistributionStatus(
    val label: String,
    val explanation: String
) {
    PERMISSION_NOT_ESTABLISHED(
        label = "Not bundled · permission not established",
        explanation =
            "The manufacturer permits access to the official document but the pinned manual restricts " +
                "reproduction or third-party redistribution without written permission. The APK therefore " +
                "contains metadata and an integrity pin, not the PDF bytes."
    )
}

internal enum class RobotManualOfflineStrategy(val label: String) {
    VERIFIED_PRIVATE_COPY("Import or download once · verified private offline copy")
}

internal data class RobotReferenceDocument(
    @RawRes val resourceId: Int? = null,
    val localFileName: String,
    val title: String,
    val revision: String,
    val relevantPages: String,
    val sourceLabel: String,
    val sourceUrl: String,
    val provenanceNote: String,
    val officialDownloadUrl: String? = null,
    val expectedSha256: String? = null,
    val expectedByteCount: Long? = null,
    val expectedPageCount: Int? = null,
    val kind: RobotReferenceDocumentKind = RobotReferenceDocumentKind.USER_MANUAL,
    val redistributionStatus: RobotManualRedistributionStatus =
        RobotManualRedistributionStatus.PERMISSION_NOT_ESTABLISHED,
    val offlineStrategy: RobotManualOfflineStrategy = RobotManualOfflineStrategy.VERIFIED_PRIVATE_COPY
)

internal data class RobotKinematicEvidence(
    val convention: String,
    val sourceLabel: String,
    val sourceUrl: String,
    val note: String,
    val containedInManual: Boolean
)

internal data class RobotArtworkEvidence(
    val reviewedAgainst: String,
    val sourceUrl: String,
    val note: String
)

internal enum class RobotReferenceEvidenceStatus(
    val shortLabel: String,
    val explanation: String
) {
    PRIMARY_MANUAL_AND_KINEMATICS_VERIFIED(
        shortLabel = "PRIMARY · DH VERIFIED",
        explanation =
            "A complete manual for the named variant and an explicit manufacturer kinematic " +
                "reference were both verified. Dataset import still requires a frame-convention " +
                "adapter and independent FK landmark tests."
    )
}

internal object IndustrialRobotReferenceCatalog {
    /** Seven previous cards failed the strict primary-evidence gate and are intentionally absent. */
    const val QUARANTINED_REFERENCE_COUNT: Int = 7

    val entries: List<IndustrialRobotReference> =
        listOf(
            IndustrialRobotReference(
                id = "ur5e",
                displayName = "Universal Robots UR5e",
                fixedVariant = "UR5e e-Series · PolyScope 5 · SW 5.25.1",
                morphology = "6-axis collaborative serial arm",
                artworkResId = R.drawable.robot_ur5e_neon,
                evidenceStatus = RobotReferenceEvidenceStatus.PRIMARY_MANUAL_AND_KINEMATICS_VERIFIED,
                specifications =
                    specs(
                        "Degrees of freedom" to "6 rotating joints",
                        "Maximum reach" to "850 mm",
                        "Payload" to "5 kg",
                        "Pose repeatability" to "±0.03 mm (ISO 9283)",
                        "Robot weight" to "20.6 kg incl. cable",
                        "Mounting" to "Any orientation",
                        "Ingress protection" to "IP54"
                    ),
                jointLimits =
                    limits(
                        "Base" to "±360° physical nominal",
                        "Shoulder" to "±360° physical nominal",
                        "Elbow" to "±360° physical nominal",
                        "Wrist 1" to "±360° physical nominal",
                        "Wrist 2" to "±360° physical nominal",
                        "Wrist 3" to "±360° physical nominal"
                    ),
                documents =
                    listOf(
                        RobotReferenceDocument(
                            localFileName = "ur5e_user_manual_sw5_25_1.pdf",
                            title = "UR5e User Manual",
                            revision = "710-965-00 · PolyScope 5.25.1 · May 2026",
                            relevantPages = "Complete manual · 241 pages",
                            sourceLabel = "Universal Robots",
                            sourceUrl =
                                "https://www.universal-robots.com/manuals/EN/PDF/SW5_25_1/user-manual-UR5e-PDF_online/710-965-00_UR5e_User_Manual_en_Global.pdf",
                            officialDownloadUrl =
                                "https://www.universal-robots.com/manuals/EN/PDF/SW5_25_1/user-manual-UR5e-PDF_online/710-965-00_UR5e_User_Manual_en_Global.pdf",
                            expectedSha256 = "619270362947717e67c3034664caa7101eff8f684f2fca34726f2c88badeaa24",
                            expectedByteCount = 20_935_077L,
                            expectedPageCount = 241,
                            provenanceNote =
                                "Downloaded only after explicit user action from the pinned manufacturer URL; " +
                                    "the app verifies byte count, SHA-256 and page count before display."
                        )
                    ),
                kinematicEvidence =
                    RobotKinematicEvidence(
                        convention = "Universal Robots nominal DH convention",
                        sourceLabel = "Universal Robots · DH Parameters",
                        sourceUrl =
                            "https://www.universal-robots.com/articles/ur/application-installation/dh-parameters-for-calculations-of-kinematics-and-dynamics",
                        note =
                            "The official table supplies nominal UR5e DH geometry. A physical controller's " +
                                "factory calibration must be applied for hardware-level accuracy; planner " +
                                "limits must not be mistaken for physical limits.",
                        containedInManual = false
                    ),
                artworkEvidence =
                    RobotArtworkEvidence(
                        reviewedAgainst = "UR5e SW 5.25.1 manual imagery and manufacturer geometry",
                        sourceUrl =
                            "https://www.universal-robots.com/manuals/EN/PDF/SW5_25_1/user-manual-UR5e-PDF_online/710-965-00_UR5e_User_Manual_en_Global.pdf",
                        note =
                            "The RGB artwork was regenerated to preserve the UR5e's six round joints, " +
                                "aluminium tubes, blue caps and proportions. It remains a stylized identifier, " +
                                "never a source of dimensions."
                    )
            ),
            IndustrialRobotReference(
                id = "franka-research-3",
                displayName = "Franka Research 3",
                fixedVariant = "FR3 Arm v2.1 · System Image 5.10",
                morphology = "7-axis collaborative research arm",
                artworkResId = R.drawable.robot_franka_research_3_neon,
                evidenceStatus = RobotReferenceEvidenceStatus.PRIMARY_MANUAL_AND_KINEMATICS_VERIFIED,
                specifications =
                    specs(
                        "Degrees of freedom" to "7",
                        "Maximum reach" to "855 mm",
                        "Rated payload" to "3 kg",
                        "Position repeatability" to "< ±0.1 mm (ISO 9283)",
                        "Robot weight" to "Approx. 18.3 kg",
                        "Ingress protection" to "IP40"
                    ),
                jointLimits =
                    limits(
                        "J1" to "±2.9007 rad",
                        "J2" to "±1.8361 rad",
                        "J3" to "±2.9007 rad",
                        "J4" to "-3.0770 to -0.1169 rad",
                        "J5" to "±2.8763 rad",
                        "J6" to "+0.4398 to +4.6216 rad",
                        "J7" to "±3.0508 rad"
                    ),
                documents =
                    listOf(
                        RobotReferenceDocument(
                            localFileName = "fr3_hardware_manual_arm_v2_1.pdf",
                            title = "Franka Research 3 Hardware Manual",
                            revision = "R02210/1.3/EN · Arm v2.1 · June 2026",
                            relevantPages = "Complete hardware manual · 118 pages",
                            sourceLabel = "Franka Robotics",
                            sourceUrl = "https://franka.de/documents",
                            officialDownloadUrl =
                                "https://franka.de/hubfs/Hardware%20Manual%20Franka%20Research%203_Arm%20v2.1_R02210_1.3_EN.pdf?hsLang=en",
                            expectedSha256 = "96b379a24293521f997bb3cc4a44a685cfc521d80f927addccbc20f65ea7f27f",
                            expectedByteCount = 10_735_910L,
                            expectedPageCount = 118,
                            provenanceNote =
                                "Pinned manufacturer original for Arm v2.1. Downloaded on demand and verified " +
                                    "before display.",
                            kind = RobotReferenceDocumentKind.HARDWARE_MANUAL
                        ),
                        RobotReferenceDocument(
                            localFileName = "fr3_operating_manual_5_10.pdf",
                            title = "Franka Research 3 Operating Manual",
                            revision = "R02216/1.3/EN · System Image 5.10 · June 2026",
                            relevantPages = "Complete operating manual · 133 pages",
                            sourceLabel = "Franka Robotics",
                            sourceUrl = "https://franka.de/documents",
                            officialDownloadUrl =
                                "https://franka.de/hubfs/Operating%20Manual%20Franka%20Research%203_5.10_R02216_1.3_EN.pdf?hsLang=en",
                            expectedSha256 = "7e5ce03c98433e6251d7e279d608a6a8c8d774ae3b1fb841e943af87382c4455",
                            expectedByteCount = 11_881_470L,
                            expectedPageCount = 133,
                            provenanceNote =
                                "Pinned manufacturer original paired with the exact System Image version. " +
                                    "Downloaded on demand and verified before display.",
                            kind = RobotReferenceDocumentKind.OPERATING_MANUAL
                        )
                    ),
                kinematicEvidence =
                    RobotKinematicEvidence(
                        convention = "Craig modified DH convention",
                        sourceLabel = "Franka Control Interface · Robot and interface specifications",
                        sourceUrl = "https://frankarobotics.github.io/docs/robot_specifications.html",
                        note =
                            "The manufacturer publishes an explicit FR3 DH table and asymmetric joint limits. " +
                                "The exact Arm v2.1 model must be retained throughout import and testing.",
                        containedInManual = false
                    ),
                artworkEvidence =
                    RobotArtworkEvidence(
                        reviewedAgainst = "Franka Research 3 Arm v2.1 manufacturer imagery",
                        sourceUrl =
                            "https://github.com/frankarobotics/franka_description/blob/main/robots/fr3v2_1/fr3v2_1.urdf.xacro",
                        note =
                            "The RGB artwork was regenerated around the white sculpted seven-axis silhouette. " +
                                "Arm revision is fixed in text because artwork cannot prove a hardware revision."
                    )
            ),
            IndustrialRobotReference(
                id = "kinova-gen3",
                displayName = "KINOVA Gen3",
                fixedVariant =
                    "Gen3 7 DoF spherical wrist · fixed base · vision module · no external actuator cable · R07/API 2.3.0",
                morphology = "7-axis collaborative serial arm",
                artworkResId = R.drawable.robot_kinova_gen3_neon,
                evidenceStatus = RobotReferenceEvidenceStatus.PRIMARY_MANUAL_AND_KINEMATICS_VERIFIED,
                specifications =
                    specs(
                        "Degrees of freedom" to "7",
                        "Maximum reach" to "902 mm",
                        "Payload" to "4 kg mid-range / 2 kg full-range",
                        "Maximum Cartesian speed" to "0.5 m/s",
                        "Robot weight" to "8.2 kg incl. vision module",
                        "Power" to "24 VDC nominal (20–30 VDC)"
                    ),
                jointLimits =
                    limits(
                        "J1" to "R07 continuous control range · no external cable",
                        "J2" to "±128.9°",
                        "J3" to "R07 continuous control range · no external cable",
                        "J4" to "±147.8°",
                        "J5" to "R07 continuous control range · no external cable",
                        "J6" to "±120.3°",
                        "J7" to "R07 continuous control range · no external cable"
                    ),
                documents =
                    listOf(
                        RobotReferenceDocument(
                            localFileName = "kinova_gen3_user_guide_r07.pdf",
                            title = "KINOVA Gen3 Ultra lightweight robot User Guide",
                            revision = "R07 · firmware/API 2.3.0 · 2022",
                            relevantPages =
                                "Complete guide: 225 pages · 7 DoF DH: Table 94, printed pages 198–199, PDF pages 204–205",
                            sourceLabel = "Kinova Robotics",
                            sourceUrl = "https://www.kinovarobotics.com/product/gen3-robots",
                            officialDownloadUrl = "https://www.kinovarobotics.com/uploads/User-Guide-Gen3-R07.pdf",
                            expectedSha256 = "353b0bc80a8c1868aac75b16ee11d8fe0e2331ef8ca87cdf63f8fd0defb00319",
                            expectedByteCount = 4_601_131L,
                            expectedPageCount = 225,
                            provenanceNote =
                                "The currently public manufacturer PDF is R07. The card is therefore pinned to " +
                                    "R07 geometry and limits instead of mixing a newer ROS model silently."
                        )
                    ),
                kinematicEvidence =
                    RobotKinematicEvidence(
                        convention = "KINOVA classical DH tables for Gen3 7 DoF",
                        sourceLabel = "KINOVA Gen3 User Guide R07",
                        sourceUrl = "https://www.kinovarobotics.com/uploads/User-Guide-Gen3-R07.pdf",
                        note =
                            "The complete manufacturer manual itself contains the 7 DoF DH table. Continuous " +
                                "joints and cable-dependent software limits must not be silently conflated.",
                        containedInManual = true
                    ),
                artworkEvidence =
                    RobotArtworkEvidence(
                        reviewedAgainst = "KINOVA Gen3 R07 manual cover and official 7 DoF model",
                        sourceUrl =
                            "https://github.com/Kinovarobotics/ros2_kortex/blob/main/kortex_description/arms/gen3/7dof/urdf/gen3_macro.xacro",
                        note =
                            "The RGB artwork was regenerated around the white modular Gen3 body and 7 DoF " +
                                "variant. It is an identifier only; official model files remain geometric truth."
                    )
            )
        )

    init {
        RobotReferenceCatalogIntegrity.requireValid(
            references = entries,
            syntheticPresetIds = DatasetRobotPresets.SYNTHETIC_PRESET_IDS,
            quarantinedReferenceCount = QUARANTINED_REFERENCE_COUNT
        )
    }

    private fun specs(vararg values: Pair<String, String>): List<RobotReferenceSpecification> =
        values.map { (label, value) -> RobotReferenceSpecification(label, value) }

    private fun limits(vararg values: Pair<String, String>): List<RobotReferenceJointLimit> =
        values.map { (joint, range) -> RobotReferenceJointLimit(joint, range) }
}

internal object RobotReferenceCatalogIntegrity {
    const val REVIEWED_COMMERCIAL_CANDIDATE_COUNT: Int = 10

    fun requireValid(
        references: List<IndustrialRobotReference>,
        syntheticPresetIds: Set<String>,
        quarantinedReferenceCount: Int
    ) {
        require(references.isNotEmpty())
        require(references.map(IndustrialRobotReference::id).distinct().size == references.size)
        require(references.none { it.id in syntheticPresetIds }) {
            "Commercial references and synthetic dataset presets must have separate identities."
        }
        require(references.size + quarantinedReferenceCount == REVIEWED_COMMERCIAL_CANDIDATE_COUNT) {
            "The commercial review ledger must account for every reviewed candidate."
        }
        references.forEach { reference ->
            require(reference.displayName.isNotBlank() && reference.fixedVariant.isNotBlank())
            require(reference.documents.isNotEmpty())
            reference.documents.forEach { document ->
                require(document.localFileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.pdf")))
                require(document.expectedSha256?.matches(Regex("[A-Fa-f0-9]{64}")) == true)
                require((document.expectedByteCount ?: 0L) in 1..(40L * 1024L * 1024L))
                require((document.expectedPageCount ?: 0) > 0)
                require(document.officialDownloadUrl?.startsWith("https://") == true)
                if (document.redistributionStatus == RobotManualRedistributionStatus.PERMISSION_NOT_ESTABLISHED) {
                    require(document.resourceId == null) {
                        "A manufacturer manual cannot be bundled while redistribution permission is unverified."
                    }
                    require(document.offlineStrategy == RobotManualOfflineStrategy.VERIFIED_PRIVATE_COPY)
                }
            }
        }
    }
}
