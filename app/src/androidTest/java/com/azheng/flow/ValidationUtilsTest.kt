package com.azheng.flow

import org.junit.Test

/**
 * @date 2025/3/26.
 * description：
 */
class ValidationUtilsTest {
    @Test
    fun isValid() {
        // Boolean校验
        ValidationUtils.isValid(true)     // true（有效）
        ValidationUtils.isValid(null as Boolean?) // false（无效）

        // 字符串校验
        ValidationUtils.isValid("Hello")  // true（有效）
        ValidationUtils.isValid("   ")    // false（纯空格无效）

        // Int/Long校验
        ValidationUtils.isValid(100 is Int)      // true（有效）
        ValidationUtils.isValid(-1 as Long?)       // false（-1无效）

        // Float校验
        ValidationUtils.isValid(3.14f)    // true（有效）
        ValidationUtils.isValid(Float.NaN) // false（NaN无效）

        // 集合校验
        ValidationUtils.isValid(listOf(1, 2, 3))  // true（非空有效）
        ValidationUtils.isValid(emptyList<String>()) // false（空集合无效）


        val validUserBean = UserBean("Alice", 25, listOf(90.5f, 80.0f))
        val invalidUserBean = UserBean("  ", -1, emptyList())

        ValidationUtils.isValid(validUserBean)   // true（所有字段有效）
        ValidationUtils.isValid(invalidUserBean) // false（name/age/scores均无效）

        // 直接通过对象调用
        "Kotlin".isValidExt()          // true

        1L.isValidExt()               // true（非-1L）
        (-1L).isValidExt()            // false

        listOf(1, 2, 3).isValidExt()    // true
        emptySet<String>().isValidExt() // false

        // 默认类型处理（非基本类型返回true）
        ValidationUtils.isValid(Any())       // true

        // 边界值测试
        ValidationUtils.isValid(0 is Int)          // true（0允许）
        ValidationUtils.isValid(-1f)        // false（-1f禁止）

    }


}