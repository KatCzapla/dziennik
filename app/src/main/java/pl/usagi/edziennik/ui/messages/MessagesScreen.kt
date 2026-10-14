package pl.usagi.edziennik.ui.messages

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import pl.usagi.edziennik.domain.MessageAttachment
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.ui.components.EDziennikTopBar
import pl.usagi.edziennik.ui.theme.EDziennikTheme
import java.time.LocalDate
import java.io.File
import java.net.URLConnection

@Composable
fun MessagesRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    MessagesScreen(
        state = state,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onRetryInitial = viewModel::retryInitialLoad,
        onLoadMore = viewModel::loadNextPage,
        onRetryNextPage = viewModel::retryNextPage,
        onMessageClick = viewModel::openMessage,
        onMailboxSelected = viewModel::selectMailbox,
        onDetailsClosed = viewModel::closeMessageDetails,
        onMessagePageSelected = viewModel::selectMessageIndex,
        onRetryMessageDetails = viewModel::retryMessageDetails,
        onDownloadAttachment = viewModel::downloadAttachment,
    )
}

@Composable
private fun MessagesScreen(
    state: MessagesUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryInitial: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryNextPage: () -> Unit,
    onMessageClick: (Int) -> Unit,
    onMailboxSelected: (MessageMailboxType) -> Unit,
    onDetailsClosed: () -> Unit,
    onMessagePageSelected: (Int) -> Unit,
    onRetryMessageDetails: (Int) -> Unit,
    onDownloadAttachment: (MessageAttachment, File, (Result<String?>) -> Unit) -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val isShowingDetails = state.isShowingDetails
    val selectedMessageIndex = state.selectedMessageIndex

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            EDziennikTopBar(
                onOpenSettings = onOpenSettings,
                showBack = true,
                onBack = if (isShowingDetails) onDetailsClosed else onBack,
            )
        },
    ) { paddingValues ->
        if (isShowingDetails && selectedMessageIndex != null) {
            MessageDetailsPager(
                state = state,
                selectedIndex = selectedMessageIndex,
                onPageSelected = onMessagePageSelected,
                onLoadMore = onLoadMore,
                onRetryMessageDetails = onRetryMessageDetails,
                onDownloadAttachment = onDownloadAttachment,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        text = "Wiadomości",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    MailboxSelector(
                        selectedMailbox = state.selectedMailbox,
                        onMailboxSelected = onMailboxSelected,
                    )
                }
            }

            when {
                state.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }

                state.errorMessage != null -> {
                    val errorMessage = state.errorMessage.orEmpty()
                    item {
                        InlineRetry(
                            message = errorMessage,
                            action = "Spróbuj ponownie",
                            onRetry = onRetryInitial,
                        )
                    }
                }

                state.messages.isEmpty() -> {
                    item {
                        Text(
                            text = state.selectedMailbox.emptyLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = state.messages,
                        key = { _, message -> message.id },
                    ) { index, message ->
                        MessageRow(
                            item = message.toMessageListItem(LocalDate.now()),
                            onClick = { onMessageClick(message.id) },
                        )

                        if (index >= state.messages.lastIndex - LOAD_MORE_THRESHOLD && state.canLoadMore) {
                            LaunchedEffect(state.messages.size, state.endReached) {
                                onLoadMore()
                            }
                        }
                    }

                    if (state.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }

                    state.nextPageErrorMessage?.let { error ->
                        item {
                            InlineRetry(
                                message = error,
                                action = "Spróbuj ponownie",
                                onRetry = onRetryNextPage,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(spacing.md))
            }
        }
    }
}

@Composable
private fun MailboxSelector(
    selectedMailbox: MessageMailboxType,
    onMailboxSelected: (MessageMailboxType) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(MessageMailboxType.entries.size) { index ->
            val mailbox = MessageMailboxType.entries[index]
            MailboxSelectorItem(
                mailbox = mailbox,
                selected = mailbox == selectedMailbox,
                onClick = { onMailboxSelected(mailbox) },
            )
        }
    }
}

@Composable
private fun MailboxSelectorItem(
    mailbox: MessageMailboxType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
    ) {
        TextButton(
            onClick = onClick,
            enabled = !selected,
            colors = ButtonDefaults.textButtonColors(
                containerColor = Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = mailbox.label(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MessageDetailsPager(
    state: MessagesUiState,
    selectedIndex: Int,
    onPageSelected: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetryMessageDetails: (Int) -> Unit,
    onDownloadAttachment: (MessageAttachment, File, (Result<String?>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = EDziennikTheme.spacing
    val pagerState = rememberPagerState(initialPage = selectedIndex) {
        state.messages.size
    }

    LaunchedEffect(pagerState, state.messages.size) {
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                if (page in state.messages.indices) {
                    onPageSelected(page)
                }
            }
    }

    LaunchedEffect(selectedIndex, state.messages.size) {
        if (selectedIndex in 0 until pagerState.pageCount && selectedIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { page -> state.messages[page].id },
        ) { page ->
            val message = state.messages[page]
            MessageDetailsPage(
                listItem = message.toMessageListItem(LocalDate.now()),
                pageState = state.messageDetails[message.id] ?: MessageDetailsPageState.Loading,
                onRetry = { onRetryMessageDetails(message.id) },
                onDownloadAttachment = onDownloadAttachment,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = spacing.lg),
            )
        }

        if (state.canLoadMore && pagerState.currentPage >= state.messages.lastIndex - LOAD_MORE_THRESHOLD) {
            LaunchedEffect(state.messages.size, state.endReached, pagerState.currentPage) {
                onLoadMore()
            }
        }
    }
}

@Composable
private fun MessageDetailsPage(
    listItem: MessageListItem,
    pageState: MessageDetailsPageState,
    onRetry: () -> Unit,
    onDownloadAttachment: (MessageAttachment, File, (Result<String?>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = EDziennikTheme.spacing

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            Text(
                text = listItem.subject,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item {
            MessageMetadataRow(
                label = "Od",
                value = listItem.sender,
            )
        }

        when (pageState) {
            MessageDetailsPageState.Loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            is MessageDetailsPageState.Error -> {
                item {
                    InlineRetry(
                        message = pageState.message,
                        action = "Spróbuj ponownie",
                        onRetry = onRetry,
                    )
                }
            }

            is MessageDetailsPageState.Loaded -> {
                item {
                    MessageDetailsContent(
                        details = pageState.details,
                        onDownloadAttachment = onDownloadAttachment,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(spacing.md))
        }
    }
}

@Composable
private fun MessageDetailsContent(
    details: MessageDetails,
    onDownloadAttachment: (MessageAttachment, File, (Result<String?>) -> Unit) -> Unit,
) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        MessageMetadata(details = details)

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        MessageHtmlContent(html = details.content)

        if (details.attachments.isNotEmpty()) {
            AttachmentsSection(
                attachments = details.attachments,
                onDownloadAttachment = onDownloadAttachment,
            )
        }
    }
}

@Composable
private fun MessageMetadata(details: MessageDetails) {
    val spacing = EDziennikTheme.spacing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MessageMetadataRow(
            label = "Data",
            value = details.date.messageDetailsDateLabel(),
        )

        details.senderInfo?.let { info ->
            MessageMetadataRow(
                label = "Informacje",
                value = info,
            )
        }
    }
}

@Composable
private fun MessageMetadataRow(
    label: String,
    value: String,
) {
    val spacing = EDziennikTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MessageMetaLine(
    label: String,
    value: String,
) {
    val spacing = EDziennikTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AttachmentsSection(
    attachments: List<MessageAttachment>,
    onDownloadAttachment: (MessageAttachment, File, (Result<String?>) -> Unit) -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val context = LocalContext.current
    var loadingUrl by remember(attachments) { mutableStateOf<String?>(null) }
    var errorMessage by remember(attachments) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = "Załączniki",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        attachments.forEach { attachment ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = "Wiadomość zawiera załącznik",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = attachment.fileName,
                    modifier = Modifier.clickable(enabled = loadingUrl == null) {
                        errorMessage = null
                        if (attachment.url.isBlank()) {
                            errorMessage = "Nie można pobrać tego załącznika."
                            return@clickable
                        }
                        val destination = try {
                            val directory = File(context.cacheDir, "message_attachments")
                            if (!directory.exists() && !directory.mkdirs()) error("Cache unavailable")
                            val extension = attachment.fileName.substringAfterLast('.', "")
                                .takeIf { it.matches(Regex("[A-Za-z0-9]{1,12}")) }
                            File.createTempFile("attachment-", extension?.let { ".$it" } ?: ".bin", directory)
                        } catch (_: Exception) {
                            errorMessage = "Nie można zapisać załącznika."
                            return@clickable
                        }
                        loadingUrl = attachment.url
                        onDownloadAttachment(attachment, destination) { result ->
                            loadingUrl = null
                            result.onSuccess { contentType ->
                                val mimeType = attachmentMimeType(contentType, attachment.fileName)
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        destination,
                                    )
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(uri, mimeType)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    )
                                } catch (_: ActivityNotFoundException) {
                                    errorMessage = "Brak aplikacji do otwarcia tego typu pliku."
                                } catch (_: Exception) {
                                    errorMessage = "Nie można otworzyć załącznika."
                                }
                            }.onFailure {
                                errorMessage = "Nie udało się pobrać załącznika. Spróbuj ponownie."
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (loadingUrl != null) {
            Surface(
                modifier = Modifier.padding(vertical = spacing.xs),
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Pobieranie załącznika...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(vertical = spacing.xxs, horizontal = spacing.xs)
                )
            }
        }
        errorMessage?.let { message ->
            Surface(
                modifier = Modifier.padding(vertical = spacing.xs),
                color = MaterialTheme.colorScheme.error,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(vertical = spacing.xxs, horizontal = spacing.xs)
                )
            }
        }
    }
}

internal fun attachmentMimeType(contentType: String?, fileName: String): String =
    contentType?.substringBefore(';')?.trim()
        ?.takeIf { it.contains('/') && it != "application/octet-stream" }
        ?: URLConnection.guessContentTypeFromName(fileName)
        ?: "*/*"

@Composable
private fun MessageRow(
    item: MessageListItem,
    onClick: () -> Unit,
) {
    val spacing = EDziennikTheme.spacing
    val senderWeight = if (item.isUnread) FontWeight.SemiBold else FontWeight.Medium
    val subjectWeight = if (item.isUnread) FontWeight.Medium else FontWeight.Normal
    val subjectColor = if (item.isUnread) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (item.isUnread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
                    },
                ),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.sender,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = senderWeight),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.dateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = item.subject,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = subjectWeight),
                    color = subjectColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.hasAttachments) {
                    Icon(
                        imageVector = Icons.Outlined.AttachFile,
                        contentDescription = "Wiadomość zawiera załącznik",
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineRetry(
    message: String,
    action: String,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(EDziennikTheme.spacing.xs)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(action)
        }
    }
}

@Composable
private fun MessageHtmlContent(html: String) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSize = MaterialTheme.typography.bodyLarge.fontSize.value
    val lineHeight = MaterialTheme.typography.bodyLarge.lineHeight.value
    val spanned = remember(html) {
        SpannableStringBuilder(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)).apply {
            Linkify.addLinks(this, Linkify.WEB_URLS)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                setLineSpacing(0f, lineHeight / textSize)
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
            textView.setLineSpacing(0f, lineHeight / textSize)
            textView.text = spanned
        },
    )
}

private fun MessageMailboxType.label(): String =
    when (this) {
        MessageMailboxType.Received -> "Odebrane"
        MessageMailboxType.Sent -> "Wysłane"
        MessageMailboxType.Deleted -> "Usunięte"
        MessageMailboxType.Drafts -> "Robocze"
    }

private fun MessageMailboxType.emptyLabel(): String =
    when (this) {
        MessageMailboxType.Received -> "Brak odebranych wiadomości"
        MessageMailboxType.Sent -> "Brak wysłanych wiadomości"
        MessageMailboxType.Deleted -> "Brak usuniętych wiadomości"
        MessageMailboxType.Drafts -> "Brak roboczych wiadomości"
    }

private const val LOAD_MORE_THRESHOLD = 5
