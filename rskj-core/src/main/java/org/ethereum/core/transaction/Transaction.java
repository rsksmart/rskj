package org.ethereum.core.transaction;


public sealed interface Transaction
        permits Type0Transaction, Type1Transaction, StandardType2Transaction, RskNamespaceTransaction {

}
