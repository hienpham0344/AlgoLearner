import React from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  LayoutDashboard,
  Hammer,
  Code2,
  FileCode2,
  ClipboardCheck,
  Activity,
  ShieldAlert,
  Users,
  Sparkles,
  BookOpen,
} from 'lucide-react';
import { WorkspaceLayout } from '@/components/layout/WorkspaceLayout';
import byteMascot from '@/assets/mascot-byte.svg';

interface FeatureMeta {
  title: string;
  subtitle: string;
  moduleCode: string;
  moduleName: string;
  description: string;
  icon: React.ElementType;
}

const FEATURE_REGISTRY: Record<string, FeatureMeta> = {
  practice: {
    title: 'Luyện tập thuật toán trực tuyến',
    subtitle: 'Online Coding Practice Studio',
    moduleCode: 'SRS Module 4.4',
    moduleName: 'Practice & Online Judge',
    description: 'Hệ thống kho bài tập thuật toán phân loại theo độ khó, chấm mã trực tiếp và thống kê chuỗi ngày luyện tập (Streak). Màn hình này đang trong giai đoạn triển khai.',
    icon: Code2,
  },
  'public-courses': {
    title: 'Danh mục Khóa học Công khai',
    subtitle: 'Public Courses Catalog',
    moduleCode: 'SRS Module 4.4',
    moduleName: 'Course Exploration',
    description: 'Thư viện các khóa học thuật toán và lập trình miễn phí dành cho học viên tự học theo lộ trình có hướng dẫn. Màn hình này đang trong giai đoạn hoàn thiện.',
    icon: BookOpen,
  },
  'teacher-assignments': {
    title: 'Quản lý Bài tập & Đánh giá',
    subtitle: 'Classroom Assignments & Grading',
    moduleCode: 'SRS Module 4.6',
    moduleName: 'Assignment Management',
    description: 'Giao diện tạo bài tập, thiết lập deadline, cấu hình nộp bài và quản lý điểm số cho sinh viên trong lớp học.',
    icon: FileCode2,
  },
  'teacher-review-queue': {
    title: 'Hàng đợi Chấm & Duyệt bài',
    subtitle: 'Submission Review Queue',
    moduleCode: 'SRS Module 4.6',
    moduleName: 'Grading & Feedback',
    description: 'Nơi giảng viên duyệt các bài nộp thủ công, chấm code và gửi nhận xét phản hồi cho sinh viên.',
    icon: ClipboardCheck,
  },
  'admin-submissions': {
    title: 'Giám sát Bài nộp toàn hệ thống',
    subtitle: 'Platform Submissions Monitor',
    moduleCode: 'SRS Module 4.6',
    moduleName: 'Submission Oversight',
    description: 'Bảng điều khiển dành cho Quản trị viên để giám sát lưu lượng nộp bài, trạng thái chấm mã và tài nguyên máy chấm.',
    icon: Activity,
  },
  'admin-system-logs': {
    title: 'Nhật ký Hệ thống & Hoạt động',
    subtitle: 'System & Activity Logs',
    moduleCode: 'SRS Module 4.1 / 4.2',
    moduleName: 'Audit & System Logging',
    description: 'Lưu trữ toàn bộ sự kiện đăng nhập, phân quyền, thay đổi cấu hình và lịch sử thao tác của các tài khoản.',
    icon: ShieldAlert,
  },
  'admin-security-audit': {
    title: 'Kiểm toán Bảo mật & Phiên làm việc',
    subtitle: 'Security & Session Audit',
    moduleCode: 'SRS Module 4.1',
    moduleName: 'Security Administration',
    description: 'Theo dõi các phiên đăng nhập bất thường, khóa tài khoản vi phạm chính sách và quản trị khóa ký JWT.',
    icon: ShieldAlert,
  },
  'admin-accounts': {
    title: 'Quản trị Danh sách Tài khoản',
    subtitle: 'User Account Moderation',
    moduleCode: 'SRS Module 4.1',
    moduleName: 'Account Management',
    description: 'Quản lý, kích hoạt, đình chỉ hoặc mở khóa tài khoản Student và Teacher trên toàn hệ thống.',
    icon: Users,
  },
};

interface PlaceholderPageProps {
  feature?: string;
}

export const PlaceholderPage: React.FC<PlaceholderPageProps> = ({ feature: propFeature }) => {
  const navigate = useNavigate();
  const { featureKey } = useParams<{ featureKey?: string }>();
  const [searchParams] = useSearchParams();

  const activeKey = propFeature || featureKey || searchParams.get('feature') || 'practice';
  const meta: FeatureMeta = FEATURE_REGISTRY[activeKey] || {
    title: searchParams.get('title') || 'Tính năng đang được phát triển',
    subtitle: 'Feature Under Construction',
    moduleCode: searchParams.get('module') || 'AlgoLearner System',
    moduleName: 'Upcoming Module',
    description: searchParams.get('desc') || 'Màn hình cho tính năng này đang được đội ngũ phát triển xây dựng theo đặc tả SRS.',
    icon: Hammer,
  };

  const Icon = meta.icon;

  return (
    <WorkspaceLayout mainClassName="dashboard-content flex items-center justify-center">
      <div className="max-w-lg w-full bg-white rounded-2xl border border-slate-200 shadow-sm p-8 text-center relative overflow-hidden my-6">
        <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-amber-500 via-emerald-500 to-indigo-500" />

        <div className="relative inline-flex items-center justify-center mb-6">
          <div className="w-20 h-20 rounded-2xl bg-amber-50 border border-amber-200/80 flex items-center justify-center shadow-inner">
            <img src={byteMascot} alt="AlgoLearner Byte" className="w-12 h-12" />
          </div>
          <div className="absolute -bottom-2 -right-2 w-8 h-8 rounded-lg bg-slate-900 text-white flex items-center justify-center shadow-md">
            <Icon size={16} />
          </div>
        </div>

        <div className="flex items-center justify-center gap-2 mb-3">
          <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-amber-100/80 text-amber-900 text-[11px] font-bold uppercase tracking-wider">
            <Sparkles size={12} className="text-amber-600" />
            {meta.moduleCode}
          </span>
          <span className="inline-flex items-center px-2.5 py-1 rounded-full bg-slate-100 text-slate-700 text-[11px] font-medium">
            Chưa có màn hình
          </span>
        </div>

        <h1 className="text-2xl font-bold tracking-tight text-slate-900 mb-1">
          {meta.title}
        </h1>
        <p className="text-xs text-slate-500 font-mono uppercase tracking-wider mb-4">
          {meta.subtitle} · {meta.moduleName}
        </p>

        <p className="text-sm text-slate-600 leading-relaxed mb-8 max-w-md mx-auto">
          {meta.description}
        </p>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
          <Link
            to="/dashboard"
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl bg-slate-900 text-white text-sm font-semibold hover:bg-slate-800 transition-all shadow-xs"
          >
            <LayoutDashboard size={16} />
            <span>Về màn hình Dashboard</span>
          </Link>
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl border border-slate-300 text-slate-700 text-sm font-semibold hover:bg-slate-50 transition-all"
          >
            <ArrowLeft size={16} />
            <span>Trang trước đó</span>
          </button>
        </div>
      </div>
    </WorkspaceLayout>
  );
};
