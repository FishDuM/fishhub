package hk.ljx.fishhub.count.biz.service;

import hk.ljx.fishhub.count.biz.domain.mapper.MqConsumeRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqIdempotentExecutorTest {

    @Mock
    private MqConsumeRecordMapper mapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private Runnable databaseAction;
    @InjectMocks
    private MqIdempotentExecutor executor;

    @Test
    void shouldExecuteDatabaseActionForFirstDelivery() {
        executeTransactionCallback();
        when(mapper.exists(anyString(), anyString())).thenReturn(0);

        assertTrue(executor.execute("count-fans", "{\"id\":1}", databaseAction));
        verify(databaseAction).run();
    }

    @Test
    void shouldSkipDuplicateDelivery() {
        executeTransactionCallback();
        when(mapper.exists(anyString(), anyString())).thenReturn(1);

        assertFalse(executor.execute("count-fans", "{\"id\":1}", databaseAction));
        verify(databaseAction, never()).run();
    }

    @SuppressWarnings("unchecked")
    private void executeTransactionCallback() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<Boolean>) invocation.getArgument(0))
                        .doInTransaction(mock(TransactionStatus.class)));
    }
}
