package com.example.sudoko

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: SudokuViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SudokuApp(vm = vm)
                }
            }
        }
    }
}

/* ---------- ViewModel ---------- */
class SudokuViewModel : ViewModel() {
    var boardState by mutableStateOf(SudokuBoard.empty())
        private set

    var selectedCell by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    var notesMode by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var elapsedTime by mutableStateOf<Long>(0L)
        private set

    private var startTime: Long = 0L
    private var endTime: Long = 0L

    init {
        newGame(Difficulty.MEDIUM)
    }

    fun newGame(d: Difficulty) {
        viewModelScope.launch(Dispatchers.Default) {
            val generated = SudokuGenerator.generate(d)
            boardState = generated
            selectedCell = null
            notesMode = false
            message = null
            elapsedTime = 0L
            startTime = System.currentTimeMillis()
            endTime = 0L
        }
    }

    fun selectCell(r: Int, c: Int) {
        if (boardState.fixed[r][c]) return
        selectedCell = r to c
    }

    fun toggleNotesMode() { notesMode = !notesMode }

    fun enterNumber(n: Int) {
        val sel = selectedCell ?: return
        val (r, c) = sel
        if (boardState.fixed[r][c]) return
        viewModelScope.launch {
            if (notesMode) {
                boardState = boardState.toggleNote(r, c, n)
            } else {
                boardState = boardState.setValue(r, c, n)
                if (boardState.isComplete()) {
                    if (SudokuGenerator.isValidSolution(boardState.values)) {
                        endTime = System.currentTimeMillis()
                        elapsedTime = endTime - startTime
                        message = "You solved it in ${formatTime(elapsedTime)}"
                    } else {
                        message = "Board complete but invalid"
                    }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun clearCell() {
        val sel = selectedCell ?: return
        val (r, c) = sel
        if (boardState.fixed[r][c]) return
        boardState = boardState.setValue(r, c, 0)
    }

    fun validateBoard() {
        message = if (SudokuGenerator.isValidPartial(boardState.values)) "So far so good" else "There are conflicts"
    }

    fun solve() {
        viewModelScope.launch(Dispatchers.Default) {
            val solved = SudokuGenerator.solve(boardState.values)
            if (solved != null) {
                boardState = SudokuBoard.fromSolved(solved, boardState.fixed)
                message = "Solved"
            } else {
                message = "No solution"
            }
        }
    }
}

/* ---------- Difficulty enum ---------- */
enum class Difficulty(val removals: Int) {
    EASY(36), MEDIUM(46), HARD(54)
}

/* ---------- SudokuBoard data class ---------- */
data class SudokuBoard(
    val values: Array<IntArray>, // 9x9 values, 0 = empty
    val fixed: Array<BooleanArray>, // whether the starting cell is fixed
    val notes: Array<Array<MutableSet<Int>>> // candidate notes for each cell
) {
    companion object {
        fun empty(): SudokuBoard {
            val v = Array(9) { IntArray(9) { 0 } }
            val f = Array(9) { BooleanArray(9) { false } }
            val n = Array(9) { Array(9) { mutableSetOf<Int>() } }
            return SudokuBoard(v, f, n)
        }

        fun fromSolved(solved: Array<IntArray>, fixedMask: Array<BooleanArray>): SudokuBoard {
            val n = Array(9) { Array(9) { mutableSetOf<Int>() } }
            val v = Array(9) { IntArray(9) }
            for (r in 0 until 9) for (c in 0 until 9) v[r][c] = solved[r][c]
            return SudokuBoard(v, fixedMask.map { it.copyOf() }.toTypedArray(), n)
        }
    }

    fun setValue(r: Int, c: Int, value: Int): SudokuBoard {
        val v = values.map { it.copyOf() }.toTypedArray()
        v[r][c] = value
        val n = notes.map { row -> row.map { it.toMutableSet() }.toTypedArray() }.toTypedArray()
        // clear notes when placing a value
        n[r][c].clear()
        return SudokuBoard(v, fixed.map { it.copyOf() }.toTypedArray(), n)
    }

    fun toggleNote(r: Int, c: Int, nValue: Int): SudokuBoard {
        val n = notes.map { row -> row.map { it.toMutableSet() }.toTypedArray() }.toTypedArray()
        if (values[r][c] != 0) return this
        if (n[r][c].contains(nValue)) n[r][c].remove(nValue) else n[r][c].add(nValue)
        return SudokuBoard(values.map { it.copyOf() }.toTypedArray(), fixed.map { it.copyOf() }.toTypedArray(), n)
    }

    fun isComplete(): Boolean = values.all { row -> row.all { it != 0 } }
}

/* ---------- SudokuGenerator & Solver (backtracking) ---------- */
object SudokuGenerator {
    private fun copyGrid(g: Array<IntArray>) = g.map { it.copyOf() }.toTypedArray()

    fun generate(difficulty: Difficulty): SudokuBoard {
        // Start with a full solved board
        val full = Array(9) { IntArray(9) { 0 } }
        fillGrid(full)
        // create fixed mask and remove cells
        val fixed = Array(9) { BooleanArray(9) { true } }
        val removals = difficulty.removals
        val positions = (0 until 81).shuffled()
        var removed = 0
        val working = copyGrid(full)
        for (pos in positions) {
            if (removed >= removals) break
            val r = pos / 9
            val c = pos % 9
            val backup = working[r][c]
            working[r][c] = 0
            // check unique solution (we'll do simpler: check that solver still finds a solution)
            val solved = solve(working)
            if (solved != null) {
                // keep removed
                fixed[r][c] = false
                removed++
            } else {
                // can't remove, restore
                working[r][c] = backup
            }
        }
        // produce initial notes empty
        val notes = Array(9) { Array(9) { mutableSetOf<Int>() } }
        return SudokuBoard(working, fixed, notes)
    }

    // Fill a grid completely using randomized backtracking
    private fun fillGrid(grid: Array<IntArray>): Boolean {
        // find empty
        for (r in 0 until 9) for (c in 0 until 9) if (grid[r][c] == 0) {
            val numbers = (1..9).shuffled()
            for (n in numbers) {
                if (isSafe(grid, r, c, n)) {
                    grid[r][c] = n
                    if (fillGrid(grid)) return true
                    grid[r][c] = 0
                }
            }
            return false
        }
        return true // full
    }

    fun solve(grid: Array<IntArray>): Array<IntArray>? {
        val working = copyGrid(grid)
        return if (solveBacktrack(working)) working else null
    }

    private fun solveBacktrack(grid: Array<IntArray>): Boolean {
        for (r in 0 until 9) for (c in 0 until 9) if (grid[r][c] == 0) {
            for (n in 1..9) {
                if (isSafe(grid, r, c, n)) {
                    grid[r][c] = n
                    if (solveBacktrack(grid)) return true
                    grid[r][c] = 0
                }
            }
            return false
        }
        return true
    }

    private fun isSafe(grid: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {
        // row/col
        for (i in 0 until 9) if (grid[row][i] == num || grid[i][col] == num) return false
        // box
        val br = (row / 3) * 3
        val bc = (col / 3) * 3
        for (r in br until br + 3) for (c in bc until bc + 3) if (grid[r][c] == num) return false
        return true
    }

    fun isValidSolution(grid: Array<IntArray>): Boolean {
        for (r in 0 until 9) for (c in 0 until 9) {
            val v = grid[r][c]
            if (v !in 1..9) return false
            // temporarily clear and test
            grid[r][c] = 0
            val ok = isSafe(grid, r, c, v)
            grid[r][c] = v
            if (!ok) return false
        }
        return true
    }

    fun isValidPartial(grid: Array<IntArray>): Boolean {
        // check duplicates in rows, cols, boxes
        for (r in 0 until 9) {
            val seen = mutableSetOf<Int>()
            for (c in 0 until 9) {
                val v = grid[r][c]
                if (v == 0) continue
                if (v in seen) return false
                seen.add(v)
            }
        }
        for (c in 0 until 9) {
            val seen = mutableSetOf<Int>()
            for (r in 0 until 9) {
                val v = grid[r][c]
                if (v == 0) continue
                if (v in seen) return false
                seen.add(v)
            }
        }
        for (br in 0 until 9 step 3) for (bc in 0 until 9 step 3) {
            val seen = mutableSetOf<Int>()
            for (r in br until br + 3) for (c in bc until bc + 3) {
                val v = grid[r][c]
                if (v == 0) continue
                if (v in seen) return false
                seen.add(v)
            }
        }
        return true
    }
}

/* ---------- Compose UI ---------- */
@Composable
fun SudokuApp(vm: SudokuViewModel) {
    val board by remember { derivedStateOf { vm.boardState } }
    val selected by remember { derivedStateOf { vm.selectedCell } }
    val notesMode by remember { derivedStateOf { vm.notesMode } }
    val message by remember { derivedStateOf { vm.message } }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Sudoku", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        SudokuGrid(board = board, selected = selected, onCellSelected = { r, c -> vm.selectCell(r, c) })

        Spacer(modifier = Modifier.height(8.dp))

        Keypad(onNumber = { vm.enterNumber(it) }, onClear = { vm.clearCell() }, notesMode = notesMode, toggleNotes = { vm.toggleNotesMode() })

        //Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.newGame(Difficulty.EASY) }) { Text("New Easy") }
            Button(onClick = { vm.newGame(Difficulty.MEDIUM) }) { Text("New Medium") }
            Button(onClick = { vm.newGame(Difficulty.HARD) }) { Text("Hard") }
        }

        //Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.validateBoard() }) { Text("Validate") }
            OutlinedButton(onClick = { vm.solve() }) { Text("Solve") }
        }

        message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color(0xFF1B5E20), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SudokuGrid(board: SudokuBoard, selected: Pair<Int, Int>?, onCellSelected: (Int, Int) -> Unit) {
    Column(modifier = Modifier.wrapContentSize()) {
        for (r in 0 until 9) {
            Row {
                for (c in 0 until 9) {
                    val isSelected = selected?.first == r && selected.second == c
                    val bg = when {
                        isSelected -> Color(0xFFD1E8FF)
                        board.fixed[r][c] -> Color(0xFFF3F3F3)
                        else -> Color.White
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(
                                start = if (c % 3 == 0) 6.dp else 1.dp,
                                top = if (r % 3 == 0) 6.dp else 1.dp,
                                end = if (c == 8) 6.dp else 1.dp,
                                bottom = if (r == 8) 6.dp else 1.dp
                            )
                            .background(bg)
                            .border(BorderStroke(1.dp, Color.LightGray), shape = RoundedCornerShape(4.dp))
                            .clickable { onCellSelected(r, c) },
                        contentAlignment = Alignment.Center
                    ) {
                        val v = board.values[r][c]
                        if (v != 0) {
                            Text(text = v.toString(), fontSize = 18.sp, fontWeight = if (board.fixed[r][c]) FontWeight.Bold else FontWeight.Medium)
                        } else {
                            // show notes small
                            val notes = board.notes[r][c]
                            if (notes.isNotEmpty()) {
                                Text(text = notes.sorted().joinToString(""), fontSize = 10.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Keypad(
    onNumber: (Int) -> Unit,
    onClear: () -> Unit,
    notesMode: Boolean,
    toggleNotes: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Numbers in 3 rows of 3 buttons
        for (row in 0 until 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (col in 1..3) {
                    val num = row * 3 + col
                    Button(
                        onClick = { onNumber(num) },
                        modifier = Modifier.size(64.dp) // Bigger buttons
                    ) {
                        Text(
                            num.toString(),
                            fontSize = 20.sp, // Larger text
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Control buttons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClear, modifier = Modifier.height(48.dp)) {
                Text("Clear", fontSize = 16.sp)
            }
            OutlinedButton(onClick = toggleNotes, modifier = Modifier.height(48.dp)) {
                Text(if (notesMode) "Notes: ON" else "Notes: OFF", fontSize = 16.sp)
            }
        }
    }
}
