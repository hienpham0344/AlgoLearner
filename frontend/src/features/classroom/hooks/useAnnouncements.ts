import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { announcementApi } from '../api/announcementApi';
import { announcementKeys } from '../api/announcementKeys';
import type {
  ClassroomAnnouncement,
  CreateAnnouncementInput,
  UpdateAnnouncementInput,
} from '../types';

export function useAnnouncementsQuery(
  classId: string,
  page = 0,
  size = 20,
  enabled = true,
) {
  return useQuery({
    queryKey: announcementKeys.list(classId, page, size),
    queryFn: ({ signal }) => announcementApi.list(classId, page, size, signal),
    enabled: Boolean(classId) && enabled,
    retry: false,
  });
}

export function useAnnouncementDetailQuery(
  classId: string,
  announcementId: string | null | undefined,
  enabled = true,
) {
  return useQuery({
    queryKey: announcementKeys.detail(classId, announcementId || ''),
    queryFn: ({ signal }) => announcementApi.get(classId, announcementId!, signal),
    enabled: Boolean(classId && announcementId) && enabled,
    retry: false,
  });
}

export function useCreateAnnouncementMutation(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateAnnouncementInput) => announcementApi.create(classId, input),
    onSuccess: async created => {
      queryClient.setQueryData(announcementKeys.detail(classId, created.id), created);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: announcementKeys.lists(classId) }),
        queryClient.invalidateQueries({ queryKey: ['notifications'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]);
    },
  });
}

export function useUpdateAnnouncementMutation(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      announcementId,
      input,
    }: {
      announcementId: string;
      input: UpdateAnnouncementInput;
    }) => announcementApi.update(classId, announcementId, input),
    onSuccess: async updated => {
      queryClient.setQueryData(announcementKeys.detail(classId, updated.id), updated);
      await queryClient.invalidateQueries({ queryKey: announcementKeys.lists(classId) });
    },
  });
}

export function useDeleteAnnouncementMutation(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (announcementId: string) => announcementApi.remove(classId, announcementId),
    onSuccess: async (_result, announcementId) => {
      queryClient.removeQueries({ queryKey: announcementKeys.detail(classId, announcementId) });
      await queryClient.invalidateQueries({ queryKey: announcementKeys.lists(classId) });
    },
  });
}

export function useTogglePinAnnouncementMutation(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (announcement: Pick<ClassroomAnnouncement, 'id' | 'isPinned'>) =>
      announcement.isPinned
        ? announcementApi.unpin(classId, announcement.id)
        : announcementApi.pin(classId, announcement.id),
    onSuccess: async updated => {
      queryClient.setQueryData(announcementKeys.detail(classId, updated.id), updated);
      await queryClient.invalidateQueries({ queryKey: announcementKeys.lists(classId) });
    },
  });
}
