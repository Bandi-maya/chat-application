//! UniFFI binding shim: exposes the repo-owned OpenMLS FRB crate
//! (third_party/openmls_android/rust) to Kotlin. Method names, argument order
//! and semantics mirror the FRB API 1:1; the Kotlin side
//! (core/crypto/MlsEngine.kt) consumes this surface.

use std::sync::Arc;

use openmls_frb::api::config::MlsGroupConfig as FrbGroupConfig;
use openmls_frb::api::credential::MlsCredential as FrbCredential;
use openmls_frb::api::engine::MlsEngine as FrbEngine;
use openmls_frb::api::keys::{serialize_signer as frb_serialize_signer, MlsSignatureKeyPair};
use openmls_frb::api::types::{
    FlexibleCommitOptions as FrbFlexibleCommitOptions, MlsCiphersuite as FrbCiphersuite,
    MlsWireFormatPolicy as FrbWireFormatPolicy, ProcessedMessageType as FrbProcessedMessageType,
};

uniffi::setup_scaffolding!("chaty_mls_native");

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum MlsNativeError {
    #[error("{msg}")]
    Operation { msg: String },
}

fn map_err(e: String) -> MlsNativeError {
    MlsNativeError::Operation { msg: e }
}

#[derive(Debug, Clone, uniffi::Enum)]
pub enum MlsCiphersuite {
    Mls128DhkemX25519Aes128gcmSha256Ed25519,
    Mls128DhkemX25519Chacha20poly1305Sha256Ed25519,
    Mls128DhkemP256Aes128gcmSha256P256,
    Mls256XwingChacha20poly1305Sha256Ed25519,
}

impl From<MlsCiphersuite> for FrbCiphersuite {
    fn from(v: MlsCiphersuite) -> Self {
        match v {
            MlsCiphersuite::Mls128DhkemX25519Aes128gcmSha256Ed25519 => {
                FrbCiphersuite::Mls128DhkemX25519Aes128gcmSha256Ed25519
            }
            MlsCiphersuite::Mls128DhkemX25519Chacha20poly1305Sha256Ed25519 => {
                FrbCiphersuite::Mls128DhkemX25519Chacha20poly1305Sha256Ed25519
            }
            MlsCiphersuite::Mls128DhkemP256Aes128gcmSha256P256 => {
                FrbCiphersuite::Mls128DhkemP256Aes128gcmSha256P256
            }
            MlsCiphersuite::Mls256XwingChacha20poly1305Sha256Ed25519 => {
                FrbCiphersuite::Mls256XwingChacha20poly1305Sha256Ed25519
            }
        }
    }
}

#[derive(uniffi::Enum)]
pub enum MlsWireFormatPolicy {
    Plaintext,
    Ciphertext,
}

impl From<MlsWireFormatPolicy> for FrbWireFormatPolicy {
    fn from(v: MlsWireFormatPolicy) -> Self {
        match v {
            MlsWireFormatPolicy::Plaintext => FrbWireFormatPolicy::Plaintext,
            MlsWireFormatPolicy::Ciphertext => FrbWireFormatPolicy::Ciphertext,
        }
    }
}

#[derive(uniffi::Record)]
pub struct MlsGroupConfig {
    pub ciphersuite: MlsCiphersuite,
    pub wire_format_policy: MlsWireFormatPolicy,
    pub use_ratchet_tree_extension: bool,
    pub max_past_epochs: u32,
    pub padding_size: u32,
    pub sender_ratchet_max_out_of_order: u32,
    pub sender_ratchet_max_forward_distance: u32,
    pub number_of_resumption_psks: u32,
}

impl From<MlsGroupConfig> for FrbGroupConfig {
    fn from(v: MlsGroupConfig) -> Self {
        FrbGroupConfig {
            ciphersuite: v.ciphersuite.into(),
            wire_format_policy: v.wire_format_policy.into(),
            use_ratchet_tree_extension: v.use_ratchet_tree_extension,
            max_past_epochs: v.max_past_epochs,
            padding_size: v.padding_size,
            sender_ratchet_max_out_of_order: v.sender_ratchet_max_out_of_order,
            sender_ratchet_max_forward_distance: v.sender_ratchet_max_forward_distance,
            number_of_resumption_psks: v.number_of_resumption_psks,
        }
    }
}

#[derive(uniffi::Record)]
pub struct CreateGroupResult {
    pub group_id: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct JoinGroupResult {
    pub group_id: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct AddMembersResult {
    pub commit: Vec<u8>,
    pub welcome: Option<Vec<u8>>,
}

#[derive(uniffi::Record)]
pub struct CommitResult {
    pub commit: Vec<u8>,
    pub welcome: Option<Vec<u8>>,
}

#[derive(uniffi::Record)]
pub struct CreateMessageResult {
    pub ciphertext: Vec<u8>,
}

#[derive(uniffi::Enum)]
pub enum ProcessedMessageType {
    Application,
    Proposal,
    StagedCommit,
}

#[derive(uniffi::Record)]
pub struct ProcessedMessageResult {
    pub message_type: ProcessedMessageType,
    pub application_message: Option<Vec<u8>>,
}

#[derive(uniffi::Record)]
pub struct KeyPackageResult {
    pub key_package_bytes: Vec<u8>,
}

#[derive(uniffi::Record)]
pub struct MlsMember {
    pub credential: Vec<u8>,
    pub index: u32,
}

#[derive(uniffi::Record)]
pub struct FlexibleCommitOptions {
    pub add_key_packages: Vec<Vec<u8>>,
    pub remove_indices: Vec<u32>,
    pub force_self_update: bool,
    pub consume_pending_proposals: bool,
    pub create_group_info: bool,
    pub use_ratchet_tree_extension: bool,
}

impl From<FlexibleCommitOptions> for FrbFlexibleCommitOptions {
    fn from(v: FlexibleCommitOptions) -> Self {
        FrbFlexibleCommitOptions {
            add_key_packages: v.add_key_packages,
            remove_indices: v.remove_indices,
            force_self_update: v.force_self_update,
            consume_pending_proposals: v.consume_pending_proposals,
            group_context_extensions: None,
            aad: None,
            create_group_info: v.create_group_info,
            use_ratchet_tree_extension: v.use_ratchet_tree_extension,
        }
    }
}

#[derive(uniffi::Object)]
pub struct ChatyMlsEngine {
    inner: FrbEngine,
}

#[uniffi::export(async_runtime = "tokio")]
impl ChatyMlsEngine {
    #[uniffi::constructor]
    pub async fn create(db_path: String, encryption_key: Vec<u8>) -> Result<Arc<Self>, MlsNativeError> {
        let engine = FrbEngine::create(db_path, encryption_key)
            .await
            .map_err(map_err)?;
        Ok(Arc::new(ChatyMlsEngine { inner: engine }))
    }

    pub async fn close(&self) -> Result<(), MlsNativeError> {
        self.inner.close().await.map_err(map_err)
    }

    pub async fn create_key_package(
        &self,
        ciphersuite: MlsCiphersuite,
        signer_bytes: Vec<u8>,
        credential_identity: Vec<u8>,
        signer_public_key: Vec<u8>,
    ) -> Result<KeyPackageResult, MlsNativeError> {
        self.inner
            .create_key_package(
                ciphersuite.into(),
                signer_bytes,
                credential_identity,
                signer_public_key,
                None,
            )
            .await
            .map(|r| KeyPackageResult {
                key_package_bytes: r.key_package_bytes,
            })
            .map_err(map_err)
    }

    pub async fn create_group(
        &self,
        config: MlsGroupConfig,
        signer_bytes: Vec<u8>,
        credential_identity: Vec<u8>,
        signer_public_key: Vec<u8>,
    ) -> Result<CreateGroupResult, MlsNativeError> {
        self.inner
            .create_group(
                config.into(),
                signer_bytes,
                credential_identity,
                signer_public_key,
                None,
                None,
            )
            .await
            .map(|r| CreateGroupResult { group_id: r.group_id })
            .map_err(map_err)
    }

    pub async fn join_group_from_welcome(
        &self,
        config: MlsGroupConfig,
        welcome_bytes: Vec<u8>,
        signer_bytes: Vec<u8>,
    ) -> Result<JoinGroupResult, MlsNativeError> {
        self.inner
            .join_group_from_welcome(config.into(), welcome_bytes, None, signer_bytes)
            .await
            .map(|r| JoinGroupResult { group_id: r.group_id })
            .map_err(map_err)
    }

    pub async fn group_epoch(&self, group_id_bytes: Vec<u8>) -> Result<u64, MlsNativeError> {
        self.inner.group_epoch(group_id_bytes).await.map_err(map_err)
    }

    pub async fn group_is_active(&self, group_id_bytes: Vec<u8>) -> Result<bool, MlsNativeError> {
        self.inner.group_is_active(group_id_bytes).await.map_err(map_err)
    }

    pub async fn group_members(
        &self,
        group_id_bytes: Vec<u8>,
    ) -> Result<Vec<MlsMember>, MlsNativeError> {
        self.inner
            .group_members(group_id_bytes)
            .await
            .map(|members| {
                members
                    .into_iter()
                    .map(|m| MlsMember {
                        credential: m.credential,
                        index: m.index,
                    })
                    .collect()
            })
            .map_err(map_err)
    }

    pub async fn add_members(
        &self,
        group_id_bytes: Vec<u8>,
        signer_bytes: Vec<u8>,
        key_packages_bytes: Vec<Vec<u8>>,
    ) -> Result<AddMembersResult, MlsNativeError> {
        self.inner
            .add_members(group_id_bytes, signer_bytes, key_packages_bytes)
            .await
            .map(|r| AddMembersResult {
                commit: r.commit,
                // FRB returns an empty Vec when no welcome was produced.
                welcome: if r.welcome.is_empty() { None } else { Some(r.welcome) },
            })
            .map_err(map_err)
    }

    pub async fn flexible_commit(
        &self,
        group_id_bytes: Vec<u8>,
        signer_bytes: Vec<u8>,
        options: FlexibleCommitOptions,
    ) -> Result<CommitResult, MlsNativeError> {
        self.inner
            .flexible_commit(group_id_bytes, signer_bytes, options.into())
            .await
            .map(|r| CommitResult {
                commit: r.commit,
                welcome: r.welcome,
            })
            .map_err(map_err)
    }

    pub async fn merge_pending_commit(&self, group_id_bytes: Vec<u8>) -> Result<(), MlsNativeError> {
        self.inner
            .merge_pending_commit(group_id_bytes)
            .await
            .map_err(map_err)
    }

    pub async fn create_message(
        &self,
        group_id_bytes: Vec<u8>,
        signer_bytes: Vec<u8>,
        message: Vec<u8>,
        aad: Vec<u8>,
    ) -> Result<CreateMessageResult, MlsNativeError> {
        self.inner
            .create_message(group_id_bytes, signer_bytes, message, Some(aad))
            .await
            .map(|r| CreateMessageResult {
                ciphertext: r.ciphertext,
            })
            .map_err(map_err)
    }

    pub async fn process_message(
        &self,
        group_id_bytes: Vec<u8>,
        message_bytes: Vec<u8>,
    ) -> Result<ProcessedMessageResult, MlsNativeError> {
        self.inner
            .process_message(group_id_bytes, message_bytes)
            .await
            .map(|r| ProcessedMessageResult {
                message_type: match r.message_type {
                    FrbProcessedMessageType::Application => ProcessedMessageType::Application,
                    FrbProcessedMessageType::Proposal => ProcessedMessageType::Proposal,
                    FrbProcessedMessageType::StagedCommit => ProcessedMessageType::StagedCommit,
                },
                application_message: r.application_message,
            })
            .map_err(map_err)
    }

    pub async fn export_secret(
        &self,
        group_id_bytes: Vec<u8>,
        label: String,
        context: Vec<u8>,
        key_length: u32,
    ) -> Result<Vec<u8>, MlsNativeError> {
        self.inner
            .export_secret(group_id_bytes, label, context, key_length)
            .await
            .map_err(map_err)
    }

    pub async fn delete_group(&self, group_id_bytes: Vec<u8>) -> Result<(), MlsNativeError> {
        self.inner.delete_group(group_id_bytes).await.map_err(map_err)
    }
}

#[uniffi::export]
pub fn signature_key_pair_generate(ciphersuite: MlsCiphersuite) -> Result<Vec<u8>, MlsNativeError> {
    let pair = MlsSignatureKeyPair::generate(ciphersuite.clone().into()).map_err(map_err)?;
    let private_key = pair.private_key();
    let public_key = pair.public_key();
    frb_serialize_signer(ciphersuite.into(), private_key, public_key).map_err(map_err)
}

#[uniffi::export]
pub fn signature_key_pair_generate_raw(
    ciphersuite: MlsCiphersuite,
) -> Result<Vec<Vec<u8>>, MlsNativeError> {
    let pair = MlsSignatureKeyPair::generate(ciphersuite.into()).map_err(map_err)?;
    let private_key = pair.private_key();
    let public_key = pair.public_key();
    Ok(vec![private_key, public_key])
}

#[uniffi::export]
pub fn serialize_signer(
    ciphersuite: MlsCiphersuite,
    private_key: Vec<u8>,
    public_key: Vec<u8>,
) -> Result<Vec<u8>, MlsNativeError> {
    frb_serialize_signer(ciphersuite.into(), private_key, public_key).map_err(map_err)
}

#[uniffi::export]
pub fn credential_identity(credential_bytes: Vec<u8>) -> Result<Vec<u8>, MlsNativeError> {
    let credential = FrbCredential::deserialize(credential_bytes).map_err(map_err)?;
    credential.identity().map_err(map_err)
}
