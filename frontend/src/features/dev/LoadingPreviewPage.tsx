import React, { useState } from 'react';
import { LoadingScreen } from '@/components/ui/LoadingScreen';

export const LoadingPreviewPage: React.FC = () => {
  const [tab, setTab] = useState<'all' | 'fullscreen' | 'page' | 'inline'>('all');

  if (tab === 'fullscreen') {
    return (
      <div>
        <div className="fixed top-4 right-4 z-50">
          <button
            type="button"
            onClick={() => setTab('all')}
            className="px-4 py-2 bg-slate-900 text-white rounded-lg text-sm font-semibold shadow-lg hover:bg-slate-800"
          >
            Đóng xem Fullscreen
          </button>
        </div>
        <LoadingScreen
          variant="fullscreen"
          message="Đang khởi tạo không gian làm việc…"
          submessage="Đang đồng bộ dữ liệu và tiến trình học tập"
        />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f6f6f4] p-8 text-slate-900 font-sans">
      <div className="max-w-4xl mx-auto space-y-8">
        {/* Header */}
        <div className="flex items-center justify-between pb-6 border-b border-slate-200">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              AlgoLearner — Loading Screen Previews
            </h1>
            <p className="text-sm text-slate-500 mt-1">
              Các mẫu trạng thái tải dữ liệu đồng bộ với nhận diện thương hiệu AlgoLearner
            </p>
          </div>
          <button
            type="button"
            onClick={() => setTab('fullscreen')}
            className="px-4 py-2 bg-emerald-700 text-white rounded-lg text-sm font-semibold shadow-sm hover:bg-emerald-800 transition-colors"
          >
            Xem thử Fullscreen Mode
          </button>
        </div>

        {/* Variant 1: Page Loading in Workspace Card */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
              1. Chế độ Page Loading (Bên trong Workspace / Khung nội dung)
            </span>
            <span className="text-xs text-slate-400">Thay thế chữ "Loading workspace…"</span>
          </div>
          <div className="bg-white border border-slate-200/90 rounded-2xl shadow-xs overflow-hidden">
            <div className="px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/50">
              <span className="text-xs font-semibold text-slate-600">Lớp học: Cấu trúc dữ liệu & Giải thuật</span>
              <span className="text-xs text-slate-400">Tab: Bài học</span>
            </div>
            <LoadingScreen
              variant="page"
              message="Đang tải danh sách bài học & tiến độ…"
              submessage="Vui lòng chờ trong giây lát khi hệ thống kết nối máy chủ"
            />
          </div>
        </div>

        {/* Variant 2: Fullscreen Preview in Box */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
              2. Mô phỏng Fullscreen Loading (Đồng bộ phiên Auth / Route chuyển đổi)
            </span>
            <span className="text-xs text-slate-400">Màn hình khởi động ứng dụng</span>
          </div>
          <div className="relative bg-[#f6f6f4] rounded-2xl border border-slate-300/80 shadow-xs overflow-hidden flex items-center justify-center p-8">
            <LoadingScreen
              variant="page"
              message="Đang chuẩn bị không gian học tập…"
              submessage="Đang kiểm tra quyền truy cập tài khoản"
            />
          </div>
        </div>

        {/* Variant 3: Inline Widget / Card Loader */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold tracking-wider text-slate-500 uppercase">
              3. Chế độ Inline (Thanh tác vụ, Widget con, Modal)
            </span>
            <span className="text-xs text-slate-400">Dành cho các khối nhỏ lẻ</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-2xs">
              <span className="text-xs font-medium text-slate-400 block mb-2">Thống kê Streak</span>
              <LoadingScreen variant="inline" message="Đang nạp dữ liệu Streak…" />
            </div>
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-2xs">
              <span className="text-xs font-medium text-slate-400 block mb-2">Hàng đợi chấm bài (Judge Queue)</span>
              <LoadingScreen variant="inline" message="Đang kiểm tra kết quả chấm mã nguồn…" />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
