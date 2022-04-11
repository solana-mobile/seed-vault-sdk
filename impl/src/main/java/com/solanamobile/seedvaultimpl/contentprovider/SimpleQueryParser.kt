package com.solanamobile.seedvaultimpl.contentprovider

import java.lang.UnsupportedOperationException

class SimpleQueryParser(
    columns: Collection<String>,
    selection: String,
    selectionArgs: Array<String>
) {
    private val numColumns = columns.size
    private val columnIndex: Int
    private val selectionArg: String

    init {
        require(selectionArgs.size == 1) { "Expected only 1 selectionArg; got $selectionArgs.size" }
        selectionArg = selectionArgs[0]
        val result = queryStringRegex.find(selection)
        require(result != null) { "Query string '$selection'is invalid" }
        val queryColumn = result.groupValues[1]
        val i = columns.indexOf(queryColumn)
        require(i != -1) { "Query string column '$queryColumn' not recognized" }
        columnIndex = i
    }

    fun match(vararg values: Any): Boolean {
        require(values.size == numColumns) { "Expected $numColumns values; got ${values.size}" }
        return when (val value = values[columnIndex]) {
            is String -> (value.trim() == selectionArg)
            is Number -> (value.toLong() == selectionArg.toLongOrNull())
            else -> throw UnsupportedOperationException("SimpleQueryParser cannot handle values of type ${value::class.simpleName}")
        }
    }

    companion object {
        // matches 'col_name = ?' and similar
        private val queryStringRegex = Regex("""^\s*(\w+)\s*=\s*\?\s*$""")
    }
}