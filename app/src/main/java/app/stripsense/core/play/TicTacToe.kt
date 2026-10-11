package app.stripsense.core.play

import kotlin.random.Random

/** Whose mark is in a square: the person's, Pattu's, or nobody's yet. */
enum class Mark { YOU, PATTU, NONE }

/**
 * Tic-tac-toe against Pattu. The nine squares run left to right, top to bottom.
 * [turn]: whose move it is. [winner]: YOU or PATTU once there are three in a row, NONE otherwise. [line]: the three
 * squares that won.
 */
data class Board(val cells: List<Mark> = List(9) { Mark.NONE }, val turn: Mark = Mark.YOU) {
    val line: List<Int>? get() = LINES.firstOrNull { (a, b, c) -> cells[a] != Mark.NONE && cells[a] == cells[b] && cells[b] == cells[c] }
    val winner: Mark get() = line?.let { cells[it[0]] } ?: Mark.NONE
    val full: Boolean get() = cells.none { it == Mark.NONE }
    val over: Boolean get() = winner != Mark.NONE || full
    val free: List<Int> get() = cells.indices.filter { cells[it] == Mark.NONE }

    /** The board after the one whose turn it is takes [square]. The same board when that is not allowed. */
    fun play(square: Int): Board {
        if (over || square !in cells.indices || cells[square] != Mark.NONE) return this
        return Board(cells.toMutableList().also { it[square] = turn }, if (turn == Mark.YOU) Mark.PATTU else Mark.YOU)
    }

    companion object {
        val LINES = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))
    }
}

/**
 * How Pattu plays. It gets better as the person's level goes up, so a beginner can win and it stays a contest:
 * at level 1 it mostly plays anywhere; from level 3 it always takes a win and always blocks one; from level 5 it
 * plays the best move there is and cannot be beaten, only drawn with.
 */
object PattuPlays {
    /** The square Pattu takes. Null when the game is over or it is not Pattu's turn. */
    fun move(board: Board, level: Int, random: Random): Int? {
        if (board.over || board.turn != Mark.PATTU) return null
        if (level >= 5) return best(board)
        val careful = level >= 3 || random.nextInt(100) < 35 + 20 * level
        if (careful) {
            finishing(board, Mark.PATTU)?.let { return it }
            finishing(board, Mark.YOU)?.let { return it }
        }
        // From level 3 it also likes the middle and the corners.
        if (level >= 3) listOf(4, 0, 2, 6, 8).filter { it in board.free }.let { good -> if (good.isNotEmpty() && random.nextInt(100) < 70) return good.first() }
        return board.free.random(random)
    }

    /** The square that gives [who] three in a row now, if there is one. */
    fun finishing(board: Board, who: Mark): Int? = board.free.firstOrNull { square ->
        Board(board.cells.toMutableList().also { it[square] = who }).winner == who
    }

    /** The best move there is, looked all the way to the end of the game. */
    fun best(board: Board): Int = board.free.maxByOrNull { -score(board.play(it)) } ?: board.free.first()

    /** How good [board] is for the one whose turn it is: 1 a sure win, 0 a draw, -1 a sure loss. */
    private fun score(board: Board): Int {
        val winner = board.winner
        if (winner != Mark.NONE) return if (winner == board.turn) 1 else -1
        if (board.full) return 0
        return board.free.maxOf { -score(board.play(it)) }
    }
}
