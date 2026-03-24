package uz.topdim.common.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Routing DataSource для Read Replicas.
 * Направляет readOnly транзакции на replica, остальные — на primary.
 *
 * Использование: @Transactional(readOnly = true) → replica
 *               @Transactional → primary
 *
 * Для активации:
 * 1. В application.yml настроить datasource.primary и datasource.replica
 * 2. Добавить @EnableReadReplica на конфигурации сервиса
 */
public class ReadWriteRoutingDataSource extends AbstractRoutingDataSource {

    private static final String PRIMARY = "primary";
    private static final String REPLICA = "replica";

    /**
     * Определяет ключ DataSource на основе типа транзакции.
     *
     * @return "replica" для readOnly, "primary" для записи
     */
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                ? REPLICA
                : PRIMARY;
    }
}
