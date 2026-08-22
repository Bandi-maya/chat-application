CREATE INDEX IF NOT EXISTS mls_control_messages_sender_device_idx
  ON public.mls_control_messages(sender_user_id, sender_device_id);
CREATE INDEX IF NOT EXISTS mls_conversation_groups_created_by_idx
  ON public.mls_conversation_groups(created_by);
CREATE INDEX IF NOT EXISTS mls_group_devices_device_idx
  ON public.mls_group_devices(user_id, device_id);
CREATE INDEX IF NOT EXISTS mls_welcomes_conversation_idx
  ON public.mls_welcomes(conversation_id);
CREATE INDEX IF NOT EXISTS mls_welcomes_sender_device_idx
  ON public.mls_welcomes(sender_user_id, sender_device_id);
