package com.example.psstool260312.utils;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

public class PinyinUtils {
    /**
     * 获取中文字符串的拼音首字母
     */
    public static String getFirstLetter(String chinese) {
        if (chinese == null || chinese.trim().isEmpty()) {
            return "#";
        }

        char firstChar = chinese.charAt(0);
        // 判断是否是汉字
        if (Character.toString(firstChar).matches("[\\u4E00-\\u9FA5]")) {
            HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
            format.setCaseType(HanyuPinyinCaseType.UPPERCASE);
            format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);

            try {
                String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar, format);
                if (pinyinArray != null && pinyinArray.length > 0) {
                    return pinyinArray[0].substring(0, 1);
                }
            } catch (BadHanyuPinyinOutputFormatCombination e) {
                e.printStackTrace();
            }
        }
        // 非汉字字符直接返回大写形式
        return Character.toString(firstChar).toUpperCase();
    }

    /**
     * 比较两个中文字符串的拼音顺序
     */
    public static int compare(String o1, String o2) {
        String pinyin1 = getFirstLetter(o1);
        String pinyin2 = getFirstLetter(o2);
        return pinyin1.compareTo(pinyin2);
    }
}