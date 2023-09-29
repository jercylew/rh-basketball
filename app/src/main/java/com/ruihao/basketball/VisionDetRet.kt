package com.ruihao.basketball

//class VisionDetRet {
//}

class VisionDetRet {
    /**
     * @return The X coordinate of the left side of the result
     */
    var left = 0
        private set

    /**
     * @return The Y coordinate of the top of the result
     */
    var top = 0
        private set

    /**
     * @return The X coordinate of the right side of the result
     */
    var right = 0
        private set

    /**
     * @return The Y coordinate of the bottom of the result
     */
    var bottom = 0
        private set

    internal constructor()

    /**
     * @param l          The X coordinate of the left side of the result
     * @param t          The Y coordinate of the top of the result
     * @param r          The X coordinate of the right side of the result
     * @param b          The Y coordinate of the bottom of the result
     */
    constructor(l: Int, t: Int, r: Int, b: Int) {
        left = l
        top = t
        right = r
        bottom = b
    }
}
