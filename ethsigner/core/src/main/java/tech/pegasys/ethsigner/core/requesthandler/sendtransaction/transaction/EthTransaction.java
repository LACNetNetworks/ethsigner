/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.ethsigner.core.requesthandler.sendtransaction.transaction;

import tech.pegasys.ethsigner.core.jsonrpc.EthSendTransactionJsonParameters;
import tech.pegasys.ethsigner.core.jsonrpc.JsonRpcRequest;
import tech.pegasys.ethsigner.core.jsonrpc.JsonRpcRequestId;
import tech.pegasys.ethsigner.core.requesthandler.sendtransaction.NonceProvider;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Uint;
import org.web3j.abi.datatypes.generated.Uint128;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.Sign.SignatureData;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpType;
import org.web3j.utils.Numeric;

public class EthTransaction implements Transaction {

  private static final Logger LOG = LogManager.getLogger();

  private static final long ONE_DAY = 86400L;
  private static final String JSON_RPC_METHOD = "eth_sendRawTransaction";
  protected final EthSendTransactionJsonParameters transactionJsonParameters;
  protected final NonceProvider nonceProvider;
  protected final JsonRpcRequestId id;
  protected BigInteger nonce;
  protected final String nodeAddress;

  public EthTransaction(
      final EthSendTransactionJsonParameters transactionJsonParameters,
      final NonceProvider nonceProvider,
      final JsonRpcRequestId id) {

    transactionJsonParameters.data(addLacchainParameters(transactionJsonParameters.data()));
    this.transactionJsonParameters = transactionJsonParameters;
    this.id = id;
    this.nonceProvider = nonceProvider;
    this.nonce = transactionJsonParameters.nonce().orElse(null);
    this.nodeAddress = "";
  }

  public EthTransaction(
      final EthSendTransactionJsonParameters transactionJsonParameters,
      final NonceProvider nonceProvider,
      final JsonRpcRequestId id,
      final String nodeAddress) {
    this.nodeAddress = nodeAddress;
    transactionJsonParameters.data(addLacchainParameters(transactionJsonParameters.data()));
    this.transactionJsonParameters = transactionJsonParameters;
    this.id = id;
    this.nonceProvider = nonceProvider;
    this.nonce = transactionJsonParameters.nonce().orElse(null);
  }

  @Override
  public void updateFieldsIfRequired() {
    if (!this.isNonceUserSpecified()) {
      this.nonce = nonceProvider.getNonce();
    }
  }

  @Override
  @NotNull
  public String getJsonRpcMethodName() {
    return JSON_RPC_METHOD;
  }

  @Override
  public byte[] rlpEncode(final SignatureData signatureData) {
    final RawTransaction rawTransaction = createTransaction();
    LOG.info("chainId {}", Numeric.toHexString(signatureData.getV()));
    if ("0x000000000009E55D".equalsIgnoreCase(Numeric.toHexString(signatureData.getV()))
        || "0x000000000009E551".equalsIgnoreCase(Numeric.toHexString(signatureData.getV()))
        || "0x000000000009E552".equalsIgnoreCase(Numeric.toHexString(signatureData.getV()))
        || "0x000000000009E554".equalsIgnoreCase(Numeric.toHexString(signatureData.getV()))) {
      return TransactionEncoder.encode(rawTransaction);
    }
    final List<RlpType> values = TransactionEncoder.asRlpValues(rawTransaction, signatureData);
    final RlpList rlpList = new RlpList(values);
    return RlpEncoder.encode(rlpList);
  }

  @Override
  public boolean isNonceUserSpecified() {
    return transactionJsonParameters.nonce().isPresent();
  }

  @Override
  public String sender() {
    return transactionJsonParameters.sender();
  }

  @Override
  public JsonRpcRequest jsonRpcRequest(
      final String signedTransactionHexString, final JsonRpcRequestId id) {
    return Transaction.jsonRpcRequest(signedTransactionHexString, id, JSON_RPC_METHOD);
  }

  @Override
  public JsonRpcRequestId getId() {
    return id;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("transactionJsonParameters", transactionJsonParameters)
        .add("nonceProvider", nonceProvider)
        .add("id", id)
        .add("nonce", nonce)
        .toString();
  }

  protected RawTransaction createTransaction() {
    return RawTransaction.createTransaction(
        nonce,
        transactionJsonParameters.gasPrice().orElse(DEFAULT_GAS_PRICE),
        transactionJsonParameters.gas().orElse(DEFAULT_GAS),
        transactionJsonParameters.receiver().orElse(DEFAULT_TO),
        transactionJsonParameters.value().orElse(DEFAULT_VALUE),
        transactionJsonParameters.data().orElse(DEFAULT_DATA));
  }

  private String addLacchainParameters(Optional<String> data) {
    Address nodeAddressHex = new Address(nodeAddress);
    String nodeAddressAbi = TypeEncoder.encode(nodeAddressHex);
    Uint expiration =
        new Uint128(new BigInteger(Long.toString((System.currentTimeMillis() / 1000L) + ONE_DAY)));
    LOG.info("expiration {}", expiration.getValue().toString());
    String expirationAbi = TypeEncoder.encode(expiration);
    String lacchainParameters = nodeAddressAbi.concat(expirationAbi);
    // Optional<String> opt = Optional.of(lacchainParameters);
    // Optional<String> newData = Stream.concat(data.stream(),
    // opt.stream()).collect(Collectors.joining(" AND "));
    return data.get().concat(lacchainParameters);
  }
}
