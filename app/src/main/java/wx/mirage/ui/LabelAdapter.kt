package wx.mirage.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

/**
 * 自定义适配器 - 为隐藏好友列表显示彩色标签
 *
 * 每个 wxId 条目显示为 "{wxId}  [{label}]" 格式，
 * 标签颜色根据 wxId 的 hashCode 从预定义颜色数组中选取。
 *
 * 从 MainActivity 中提取为独立文件，提高代码可维护性。
 *
 * @param context Android Context
 * @param ids 隐藏好友 wxId 列表
 * @param labels wxId 到标签的映射
 */
class LabelAdapter(
    context: Context,
    private val ids: List<String>,
    private val labels: Map<String, String>
) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, ids) {

    private val labelColors = arrayOf(
        Color.parseColor("#2196F3"), // 蓝色
        Color.parseColor("#4CAF50"), // 绿色
        Color.parseColor("#FF9800"), // 橙色
        Color.parseColor("#9C27B0"), // 紫色
        Color.parseColor("#E91E63"), // 粉色
        Color.parseColor("#00BCD4"), // 青色
        Color.parseColor("#FF5722"), // 深橙
        Color.parseColor("#3F51B5"), // 靛蓝
    )

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        val wxId = ids[position]
        val label = labels[wxId]

        if (!label.isNullOrEmpty()) {
            val colorIndex = kotlin.math.abs(wxId.hashCode()) % labelColors.size
            view.text = "$wxId  [$label]"
            view.setTextColor(labelColors[colorIndex])
        } else {
            view.text = wxId
            view.setTextColor(Color.BLACK)
        }
        view.textSize = 15f
        view.setPadding(16, 12, 16, 12)

        return view
    }
}