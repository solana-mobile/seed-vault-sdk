/*
 * Copyright (c) 2024 Solana Mobile Inc.
 */

package com.solanamobile.seedvault.cts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.solanamobile.seedvault.cts.data.ConditionCheckManifest
import com.solanamobile.seedvault.cts.data.TestCase
import com.solanamobile.seedvault.cts.data.TestCorpus
import com.solanamobile.seedvault.cts.data.TestResult
import com.solanamobile.seedvault.cts.data.TestSessionLogger
import com.solanamobile.seedvault.cts.data.TestSessionRepository
import com.solanamobile.ui.apptheme.Sizes
import com.solanamobile.ui.apptheme.SolanaTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class SetupActivity : ComponentActivity() {
    @Inject
    lateinit var testSessionRepository: TestSessionRepository

    @Inject
    lateinit var logger: TestSessionLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val testDetails = testSessionRepository.test.collectAsState().value
            val navController = rememberNavController()
            SolanaTheme {
                NavHost(
                    navController = navController,
                    startDestination = "testFlow",
                ) {
                    composable(route = "testFlow") {
                        TestFlow(
                            testDetails = testDetails,
                            hasPrevious = testSessionRepository.hasPrevious(),
                            hasNext = testSessionRepository.hasNext(),
                            endTesting = {
                                testSessionRepository.end()
                                navController.navigate("results") {
                                    popUpTo("testFlow") {
                                        inclusive = true
                                    }
                                }
                            },
                            moveToPreviousTest = { testSessionRepository.reverse() },
                            moveToNextTest = { testSessionRepository.advance() }
                        )
                    }
                    composable("results") {
                        AllTestResults(
                            testSessionRepository.testCorpus,
                            logger
                        )
                    }
                }
            }
        }
        // This must be called unconditionally, in case Activity is destroyed and recreated while
        // a startActivityForResult Intent is in progress.
        testSessionRepository.registerActivityResultLaunchers(this@SetupActivity)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AllTestResults(
    testCorpus: TestCorpus,
    logger: TestSessionLogger,
) {
    val results = testCorpus.map {
        it.id to it.state.collectAsState().value
    }
    val context = LocalContext.current
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                testTagsAsResourceId = true
                testTag = "TestSummaryScaffold"
            },
        topBar = {
            TopAppBar(
                title = { Text(text = "Test Results") },
                actions = {
                    IconButton(
                        modifier = Modifier
                            .size(Sizes.dp48),
                        onClick = {
                            val filePath =
                                context.getFileStreamPath(logger.getSessionFilename()).absolutePath
                            val logFileUri = FileProvider.getUriForFile(
                                context,
                                context.applicationContext.packageName + ".provider",
                                File(filePath)
                            )
                            val shareIntent = ShareCompat.IntentBuilder(context)
                                .addStream(logFileUri)
                                .setType("text/plain")
                                .intent
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            startActivity(context, shareIntent, null)
                        },
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(Sizes.dp28),
                            tint = MaterialTheme.colorScheme.onSurface,
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_test_results)
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Sizes.dp16)
                    .padding(horizontal = Sizes.dp32),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    modifier = Modifier
                        .vertical()
                        .rotate(-90f),
                    text = stringResource(R.string.test_name),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    modifier = Modifier
                        .vertical()
                        .rotate(-90f),
                    text = stringResource(id = R.string.preconditions),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    modifier = Modifier
                        .vertical()
                        .rotate(-90f),
                    text = stringResource(id = R.string.test_result),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    modifier = Modifier
                        .vertical()
                        .rotate(-90f),
                    text = stringResource(id = R.string.test_overall_result),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(Sizes.dp16),
                horizontalArrangement = Arrangement.spacedBy(Sizes.dp4)
            ) {
                items(4) { item ->
                    when (item) {
                        0, 1, 2 -> Unit
                        3 -> TestResult(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(all = Sizes.dp4)
                                .semantics {
                                    testTag = "OverallResult"
                                },
                            testResult = TestResult.resolve(*results.map { it.second.overall }
                                .toTypedArray())
                        )
                    }
                }
                items(
                    count = results.size * 4,
                ) { item ->
                    val index = item / 4
                    when (item % 4) {
                        0 -> {
                            Text(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(vertical = Sizes.dp16),
                                textAlign = TextAlign.Center,
                                text = results[index].first,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        1 -> {
                            TestResult(
                                modifier = Modifier
                                    .padding(all = Sizes.dp4)
                                    .aspectRatio(1f),
                                testResult = TestResult.resolve(*results[index].second.preConditions.map { it.result }
                                    .toTypedArray())
                            )
                        }

                        2 -> {
                            TestResult(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(all = Sizes.dp4),
                                testResult = results[index].second.result
                            )
                        }

                        3 -> {
                            TestResult(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(all = Sizes.dp4),
                                testResult = results[index].second.overall
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun TestFlow(
    testDetails: TestCase,
    hasPrevious: Boolean,
    hasNext: Boolean,
    moveToNextTest: () -> Unit,
    moveToPreviousTest: () -> Unit,
    endTesting: () -> Unit
) {
    val testState = testDetails.state.collectAsState().value
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .semantics {
                testTagsAsResourceId = true
                testTag = "TestStateScaffold"
            },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (hasPrevious) {
                        IconButton(
                            modifier = Modifier
                                .size(Sizes.dp48),
                            onClick = moveToPreviousTest,
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(Sizes.dp28),
                                tint = MaterialTheme.colorScheme.onSurface,
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.previous_test)
                            )
                        }
                    }
                },
                title = {
                    Text(
                        modifier = Modifier.semantics {
                            testTag = "TestId"
                        },
                        text = testDetails.id
                    )
                },
                actions = {
                    if (hasNext) {
                        IconButton(
                            modifier = Modifier
                                .size(Sizes.dp48)
                                .semantics {
                                    testTag = "NextTest"
                                },
                            onClick = moveToNextTest,
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(Sizes.dp28),
                                tint = MaterialTheme.colorScheme.onSurface,
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.next_test)
                            )
                        }
                    }
                    IconButton(
                        modifier = Modifier
                            .size(Sizes.dp48)
                            .semantics {
                                testTag = "Finish"
                            },
                        onClick = endTesting,
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(Sizes.dp28),
                            tint = MaterialTheme.colorScheme.onSurface,
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.finish)
                        )
                    }
                },
            )
        }
    )
    { innerPadding ->
        TestStateView(
            modifier = Modifier.padding(innerPadding),
            testDetails = testDetails,
            testState = testState
        )
    }
}

@Composable
fun TestStateView(
    modifier: Modifier = Modifier,
    testDetails: TestCase,
    testState: TestCase.State?,
) {
    val coroutine = rememberCoroutineScope()
    Column(
        modifier = modifier
            .semantics {
                testTag = "TestStateContainer"
            }
            .fillMaxWidth()
            .padding(horizontal = Sizes.dp16)
            .verticalScroll(rememberScrollState())
    ) {
        if (testDetails.description.isNotBlank()) {
            Header(text = stringResource(R.string.test_description))
            Text(
                text = testDetails.description,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        if (testDetails.instructions.isNotBlank()) {
            Header(text = stringResource(R.string.test_instructions))
            Text(
                text = testDetails.instructions,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Button(
            modifier = Modifier
                .padding(vertical = Sizes.dp16)
                .semantics {
                    testTag = "ValidateAndExecute"
                },
            enabled = testState?.overall != TestResult.PASS,
            onClick = {
                coroutine.launch {
                    testDetails.validatePreconditions()
                    testDetails.execute()
                }
            }
        ) {
            Text(
                text = stringResource(R.string.validate_execute)
            )
        }

        testState?.let {
            Header(text = stringResource(R.string.test_overall_result))
            TestResult(
                modifier = Modifier
                    .padding(bottom = Sizes.dp16)
                    .semantics {
                        testTag = "TestResult"
                    },
                testResult = it.overall
            )

            if (it.preConditions.isNotEmpty()) {
                Header(text = stringResource(R.string.preconditions))
                Conditions(state = it.preConditions)
            }

            Header(text = stringResource(R.string.test_result))
            TestResult(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = Sizes.dp16),
                testResult = it.result
            )
        }
    }
}

@Composable
fun Conditions(state: ConditionCheckManifest) {
    state.forEach { precondition ->
        Card(
            modifier = Modifier
                .padding(vertical = Sizes.dp16)
                .fillMaxWidth()
        ) {
            Text(
                modifier = Modifier
                    .padding(all = Sizes.dp16),
                text = "Id: " + precondition.id,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                modifier = Modifier
                    .padding(horizontal = Sizes.dp16),
                text = precondition.description,
                style = MaterialTheme.typography.bodyLarge
            )
            TestResult(
                modifier = Modifier
                    .padding(all = Sizes.dp16),
                testResult = precondition.result
            )
        }
    }
}

@Composable
fun TestResult(
    modifier: Modifier = Modifier,
    testResult: TestResult
) {
    Text(
        modifier = modifier
            .clip(RoundedCornerShape(Sizes.dp8))
            .background(
                when (testResult) {
                    TestResult.UNEVALUATED -> MaterialTheme.colorScheme.surfaceVariant
                    TestResult.PASS -> MaterialTheme.colorScheme.primary
                    TestResult.FAIL -> MaterialTheme.colorScheme.errorContainer
                    TestResult.EMPTY -> MaterialTheme.colorScheme.primary
                }
            )
            .padding(Sizes.dp8),
        text = testResult.name,
        style = MaterialTheme.typography.titleMedium,
        color = when (testResult) {
            TestResult.UNEVALUATED -> MaterialTheme.colorScheme.onSurfaceVariant
            TestResult.PASS -> MaterialTheme.colorScheme.onPrimary
            TestResult.FAIL -> MaterialTheme.colorScheme.onErrorContainer
            TestResult.EMPTY -> MaterialTheme.colorScheme.onPrimary
        }
    )
}

@Composable
fun Header(text: String) {
    Text(
        modifier = Modifier.padding(vertical = Sizes.dp16),
        text = text,
        style = MaterialTheme.typography.titleLarge
    )
}

fun Modifier.vertical() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2)
        )
    }
}
