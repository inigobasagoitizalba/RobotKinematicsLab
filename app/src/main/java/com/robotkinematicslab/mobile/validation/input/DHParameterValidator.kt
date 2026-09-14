package com.robotkinematicslab.mobile.validation.input

import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.logging.AppLog

class DHParameterValidator {

    companion object {
        private const val TAG = "DHParameterValidator"
    }

    fun isValid(parameter: DHParameter): Boolean {
        AppLog.d(TAG) {
            "🔍 isValid called | parameter={theta=${parameter.theta}, d=${parameter.d}, a=${parameter.a}, alpha=${parameter.alpha}}"
        }

        val thetaFinite = parameter.theta.isFinite()
        val dFinite = parameter.d.isFinite()
        val aFinite = parameter.a.isFinite()
        val alphaFinite = parameter.alpha.isFinite()

        AppLog.d(TAG) {
            "🧪 DH component finiteness checked | thetaFinite=$thetaFinite, dFinite=$dFinite, aFinite=$aFinite, alphaFinite=$alphaFinite"
        }

        val isValid = thetaFinite &&
                dFinite &&
                aFinite &&
                alphaFinite

        if (isValid) {
            AppLog.d(TAG) {
                "✅ isValid finished | result=true"
            }
        } else {
            AppLog.w(TAG) {
                "⚠️ isValid finished | result=false, parameter={theta=${parameter.theta}, d=${parameter.d}, a=${parameter.a}, alpha=${parameter.alpha}}"
            }
        }

        return isValid
    }

    fun validateAll(parameters: List<DHParameter>): Boolean {
        AppLog.d(TAG) {
            "🚀 validateAll started | parameterCount=${parameters.size}"
        }

        parameters.forEachIndexed { index, parameter ->
            AppLog.d(TAG) {
                "🔄 Validating DH parameter | index=$index, parameter={theta=${parameter.theta}, d=${parameter.d}, a=${parameter.a}, alpha=${parameter.alpha}}"
            }

            val valid = isValid(parameter)

            if (!valid) {
                AppLog.w(TAG) {
                    "❌ validateAll failed | firstInvalidIndex=$index"
                }
                return false
            }

            AppLog.d(TAG) {
                "✅ DH parameter valid | index=$index"
            }
        }

        AppLog.d(TAG) {
            "🏁 validateAll finished | result=true, validatedCount=${parameters.size}"
        }

        return true
    }
}