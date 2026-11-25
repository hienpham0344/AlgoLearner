export const announcementKeys = {
  all: ['announcements'] as const,
  byClass: (classId: string) => [...announcementKeys.all, classId] as const,
  lists: (classId: string) => [...announcementKeys.byClass(classId), 'list'] as const,
  list: (classId: string, page: number, size = 20) =>
    [...announcementKeys.lists(classId), { page, size }] as const,
  details: (classId: string) => [...announcementKeys.byClass(classId), 'detail'] as const,
  detail: (classId: string, announcementId: string) =>
    [...announcementKeys.details(classId), announcementId] as const,
};
