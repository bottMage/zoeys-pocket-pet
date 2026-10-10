package com.example.shortsgesturecontrol

/** Hand-traced contours in a 592 x 400 reference panel. Shared with preview tooling. */
internal object CareBlobArt {
    data class Blob(val outline: String, val x: Float, val y: Float)
    val categories = listOf(
        // FUN / REST / CLEAN / FOOD / HEALTH are a true partition. Every
        // internal curve below is repeated in reverse by its neighbor.
        Blob("M 28 0 L 185 0 C 225 15 195 65 230 110 C 260 145 275 160 245 185 C 235 182 225 175 205 165 C 155 145 60 150 0 140 L 0 28 C 0 12 12 0 28 0 Z", 112f, 82f),
        Blob("M 185 0 L 395 0 C 365 25 420 70 380 110 C 360 140 350 165 345 185 C 320 225 270 220 245 185 C 275 160 260 145 230 110 C 195 65 225 15 185 0 Z", 300f, 98f),
        Blob("M 395 0 L 564 0 C 580 0 592 12 592 28 L 592 160 C 485 155 430 175 345 185 C 350 165 360 140 380 110 C 420 70 365 25 395 0 Z", 490f, 82f),
        Blob("M 0 140 C 60 150 155 145 205 165 C 225 175 235 182 245 185 C 220 250 250 365 300 400 L 28 400 C 12 400 0 388 0 372 Z", 128f, 278f),
        Blob("M 345 185 C 430 175 485 155 592 160 L 592 372 C 592 388 580 400 564 400 L 300 400 C 250 365 220 250 245 185 C 270 220 320 225 345 185 Z", 460f, 278f)
    )
    val two = listOf(
        Blob("M 28 0 L 296 0 C 270 60 320 110 296 250 C 210 270 120 225 0 250 L 0 28 C 0 12 12 0 28 0 Z", 143f, 120f),
        Blob("M 296 0 L 564 0 C 580 0 592 12 592 28 L 592 250 C 470 225 380 270 296 250 C 320 110 270 60 296 0 Z", 449f, 120f)
    )
    val backTwo = Blob("M 0 250 C 120 225 210 270 296 250 C 380 270 470 225 592 250 L 592 372 C 592 388 580 400 564 400 L 28 400 C 12 400 0 388 0 372 Z", 296f, 330f)
    val three = listOf(
        Blob("M 28 0 L 296 0 C 270 60 320 110 296 200 C 225 185 140 210 0 190 L 0 28 C 0 12 12 0 28 0 Z", 140f, 100f),
        Blob("M 296 0 L 564 0 C 580 0 592 12 592 28 L 592 190 C 450 210 370 185 296 200 C 320 110 270 60 296 0 Z", 452f, 100f),
        Blob("M 0 190 C 140 210 225 185 296 200 C 260 260 300 330 296 400 L 28 400 C 12 400 0 388 0 372 Z", 143f, 290f)
    )
    val backThree = Blob("M 296 200 C 370 185 450 210 592 190 L 592 372 C 592 388 580 400 564 400 L 296 400 C 300 330 260 260 296 200 Z", 444f, 300f)
}
