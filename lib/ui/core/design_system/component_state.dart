enum ChatyComponentState {
  idle,
  loading,
  empty,
  ready,
  offline,
  queued,
  uploading,
  success,
  error,
  permissionDenied,
  selected,
  locked,
  disabled,
}

enum ChatyDeliveryState {
  local,
  queued,
  sending,
  sent,
  delivered,
  read,
  failed,
}

enum ChatyPresenceState {
  offline,
  online,
  typing,
  recording,
  calling,
}
