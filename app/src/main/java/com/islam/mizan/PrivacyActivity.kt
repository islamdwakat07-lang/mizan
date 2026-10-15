package com.islam.mizan

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/** سياسة الخصوصية: Health Connect يطلبها قبل السماح بقراءة الخطوات */
class PrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this).apply {
            textSize = 17f
            setPadding(48, 64, 48, 64)
            text = """
                سياسة الخصوصية - ميزان

                يقرأ ميزان عدد خطواتك اليومي من Health Connect فقط، ليحسب تقدّمك بالمشي ويذكّرك إذا قصّرت.

                كل بياناتك (الوزن، الأكل، الماء، الخطوات، التمارين) تُحفظ على جوالك فقط ولا تُرسل لأي جهة.

                الاستثناء الوحيد: إذا استخدمت «المستشار» ووضعت مفتاح Anthropic API، يُرسل وصف الأكلة أو صورتها مع أرقام يومك إلى Anthropic للحصول على النصيحة.

                تقدر تلغي إذن الخطوات بأي وقت من إعدادات Health Connect.
            """.trimIndent()
        }
        setContentView(ScrollView(this).apply { addView(t) })
    }
}
